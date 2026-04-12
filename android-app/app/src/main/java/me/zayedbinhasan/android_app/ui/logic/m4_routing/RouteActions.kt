package me.zayedbinhasan.android_app.ui.logic.m4_routing

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.logic.m6_triage.applyTriagePreemption
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

internal fun canManageRouteActions(role: String): Boolean {
    return role == "SUPPLY_MANAGER" || role == "CAMP_COMMANDER" || role == "SYNC_ADMIN"
}

internal fun insertDemoRoute(repository: LocalRepository, vehicle: String = "TRUCK") {
    val now = System.currentTimeMillis()
    val routeId = "route-${UUID.randomUUID().toString().take(8)}"
    val normalizedVehicle = vehicle.uppercase()
    val defaultEdges = when (normalizedVehicle) {
        "SPEEDBOAT" -> "E6,E7"
        "DRONE" -> "E1,E3"
        else -> "E1,E3"
    }
    val defaultDuration = when (normalizedVehicle) {
        "SPEEDBOAT" -> 200L
        "DRONE" -> 65L
        else -> 65L
    }

    repository.upsertRoute(
        routeId = routeId,
        edgeIdsJson = defaultEdges,
        totalDurationMins = defaultDuration,
        vehicle = normalizedVehicle,
        etaTimestamp = now + defaultDuration * 60_000,
        reasonCode = "INITIAL_PLAN",
        updatedAt = now,
    )
    appendMutation(
        repository = repository,
        entityType = "route",
        entityId = routeId,
        operationType = "UPSERT",
        changedFieldsJson = JSONObject().apply {
            put("edge_ids", defaultEdges)
            put("vehicle", normalizedVehicle)
            put("duration_mins", defaultDuration)
            put("reason", "INITIAL_PLAN")
        }.toString(),
    )
}

internal fun deleteRoute(repository: LocalRepository, routeId: String) {
    repository.deleteRouteById(routeId)
    appendMutation(repository, entityType = "route", entityId = routeId, operationType = "DELETE")
}

internal data class RouteRefreshSummary(
    val success: Boolean,
    val changedRouteCount: Int,
    val blockedRouteCount: Int,
    val preemptedLowPriorityCount: Int,
    val reroutedHighPriorityCount: Int,
    val reasonCode: String,
    val recomputedAt: Long,
    val detail: String,
)

private data class ChaosEdge(
    val id: String,
    val source: String,
    val target: String,
    val type: String,
    val baseWeightMins: Long,
    val isFlooded: Boolean,
)

private data class DijkstraResult(
    val edgeIds: List<String>,
    val totalDurationMins: Long,
)

internal fun refreshRoutesFromChaos(
    repository: LocalRepository,
    chaosBaseUrl: String,
): RouteRefreshSummary {
    val now = System.currentTimeMillis()
    val edges = fetchChaosEdges(chaosBaseUrl) ?: return RouteRefreshSummary(
        success = false,
        changedRouteCount = 0,
        blockedRouteCount = 0,
        preemptedLowPriorityCount = 0,
        reroutedHighPriorityCount = 0,
        reasonCode = "CHAOS_UNAVAILABLE",
        recomputedAt = now,
        detail = "Cannot fetch $chaosBaseUrl/api/network/status",
    )

    if (repository.allRoutesNow().isEmpty()) {
        insertDemoRoute(repository)
    }

    val routeRows = repository.allRoutesNow()
    if (routeRows.isEmpty()) {
        return RouteRefreshSummary(
            success = false,
            changedRouteCount = 0,
            blockedRouteCount = 0,
            preemptedLowPriorityCount = 0,
            reroutedHighPriorityCount = 0,
            reasonCode = "NO_ROUTES",
            recomputedAt = now,
            detail = "No routes available",
        )
    }

    val edgeById = edges.associateBy { it.id }
    var changedRouteCount = 0
    var blockedRouteCount = 0
    var preemptedLowPriorityCount = 0
    var reroutedHighPriorityCount = 0
    var summaryReason = "NO_CHANGE"

    routeRows.forEach { route ->
        val routeEdgeIds = parseRouteEdgeIds(route.edge_ids_json)
        val resolvedSources = routeEdgeIds.mapNotNull { edgeId: String -> edgeById[edgeId]?.source }
        val resolvedTargets = routeEdgeIds.mapNotNull { edgeId: String -> edgeById[edgeId]?.target }
        val startNode = resolvedSources.firstOrNull() ?: "N1"
        val targetNode = resolvedTargets.lastOrNull() ?: "N4"

        val recomputed = computeShortestPath(
            edges = edges,
            startNode = startNode,
            targetNode = targetNode,
            vehicle = route.vehicle,
        )

        if (recomputed == null) {
            blockedRouteCount += 1
            summaryReason = "CHAOS_BLOCKED"
            if (route.reason_code != "CHAOS_BLOCKED") {
                repository.upsertRoute(
                    routeId = route.route_id,
                    edgeIdsJson = route.edge_ids_json,
                    totalDurationMins = route.total_duration_mins,
                    vehicle = route.vehicle,
                    etaTimestamp = route.eta_timestamp,
                    reasonCode = "CHAOS_BLOCKED",
                    updatedAt = now,
                )

                appendMutation(
                    repository = repository,
                    entityType = "route",
                    entityId = route.route_id,
                    operationType = "ROUTE_RECOMPUTE_BLOCKED",
                    changedFieldsJson = JSONObject().apply {
                        put("vehicle", route.vehicle)
                        put("start", startNode)
                        put("target", targetNode)
                        put("reason", "no_path_for_vehicle_constraints")
                    }.toString(),
                )
            }

            val triageImpact = applyTriagePreemption(
                repository = repository,
                routeId = route.route_id,
                previousDurationMins = route.total_duration_mins,
                recomputedDurationMins = route.total_duration_mins,
                blocked = true,
                reasonCode = "CHAOS_BLOCKED",
            )
            preemptedLowPriorityCount += triageImpact.preemptedLowPriorityCount
            reroutedHighPriorityCount += triageImpact.reroutedHighPriorityCount
            return@forEach
        }

        val newEdgeIdsJson = recomputed.edgeIds.joinToString(separator = ",")
        val oldEdgeIdsNormalized = parseRouteEdgeIds(route.edge_ids_json)
        val routeChanged = oldEdgeIdsNormalized != recomputed.edgeIds ||
            route.total_duration_mins != recomputed.totalDurationMins

        val reasonCode = when {
            routeChanged -> "CHAOS_REROUTED"
            route.reason_code == "CHAOS_BLOCKED" -> "CHAOS_RECOVERED"
            else -> "NO_CHANGE"
        }
        val routeStateChanged = routeChanged || route.reason_code != reasonCode

        if (routeStateChanged) {
            changedRouteCount += 1
            summaryReason = reasonCode
        }

        val etaTimestamp = now + recomputed.totalDurationMins * 60_000
        if (routeStateChanged) {
            repository.upsertRoute(
                routeId = route.route_id,
                edgeIdsJson = newEdgeIdsJson,
                totalDurationMins = recomputed.totalDurationMins,
                vehicle = route.vehicle,
                etaTimestamp = etaTimestamp,
                reasonCode = reasonCode,
                updatedAt = now,
            )

            appendMutation(
                repository = repository,
                entityType = "route",
                entityId = route.route_id,
                operationType = if (routeChanged) "ROUTE_RECOMPUTED" else "ROUTE_STATE_UPDATED",
                changedFieldsJson = JSONObject().apply {
                    put("vehicle", route.vehicle)
                    put("old_duration_mins", route.total_duration_mins)
                    put("new_duration_mins", recomputed.totalDurationMins)
                    put("old_edges", JSONArray(oldEdgeIdsNormalized))
                    put("new_edges", JSONArray(recomputed.edgeIds))
                    put("old_reason_code", route.reason_code)
                    put("new_reason_code", reasonCode)
                }.toString(),
            )
        }

        val triageImpact = applyTriagePreemption(
            repository = repository,
            routeId = route.route_id,
            previousDurationMins = route.total_duration_mins,
            recomputedDurationMins = recomputed.totalDurationMins,
            blocked = false,
            reasonCode = reasonCode,
        )
        preemptedLowPriorityCount += triageImpact.preemptedLowPriorityCount
        reroutedHighPriorityCount += triageImpact.reroutedHighPriorityCount
    }

    val finalReason = when {
        blockedRouteCount > 0 -> "CHAOS_BLOCKED"
        changedRouteCount > 0 -> "CHAOS_REROUTED"
        preemptedLowPriorityCount > 0 || reroutedHighPriorityCount > 0 -> "TRIAGE_UPDATED"
        else -> summaryReason
    }

    return RouteRefreshSummary(
        success = true,
        changedRouteCount = changedRouteCount,
        blockedRouteCount = blockedRouteCount,
        preemptedLowPriorityCount = preemptedLowPriorityCount,
        reroutedHighPriorityCount = reroutedHighPriorityCount,
        reasonCode = finalReason,
        recomputedAt = now,
        detail = "Routes=${routeRows.size}, changed=$changedRouteCount, blocked=$blockedRouteCount",
    )
}

private fun fetchChaosEdges(chaosBaseUrl: String): List<ChaosEdge>? {
    return runCatching {
        val url = URL("$chaosBaseUrl/api/network/status")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
            setRequestProperty("Accept", "application/json")
        }

        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()

        connection.disconnect()
        if (responseCode !in 200..299 || body.isBlank()) {
            return null
        }

        val root = JSONObject(body)
        val edgesArray = root.optJSONArray("edges") ?: JSONArray()
        val edges = mutableListOf<ChaosEdge>()
        for (index in 0 until edgesArray.length()) {
            val edgeJson = edgesArray.optJSONObject(index) ?: continue
            val edge = ChaosEdge(
                id = edgeJson.optString("id"),
                source = edgeJson.optString("source"),
                target = edgeJson.optString("target"),
                type = edgeJson.optString("type").lowercase(),
                baseWeightMins = edgeJson.optLong("base_weight_mins", 9999L),
                isFlooded = edgeJson.optBoolean("is_flooded", false),
            )
            if (edge.id.isNotBlank() && edge.source.isNotBlank() && edge.target.isNotBlank()) {
                edges += edge
            }
        }
        edges
    }.getOrNull()
}

private fun parseRouteEdgeIds(rawEdgeIds: String): List<String> {
    val trimmed = rawEdgeIds.trim()
    if (trimmed.isBlank()) {
        return emptyList()
    }

    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        return runCatching {
            val jsonArray = JSONArray(trimmed)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val edgeId = jsonArray.optString(index).trim()
                    if (edgeId.isNotBlank()) {
                        add(edgeId)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    return trimmed.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun computeShortestPath(
    edges: List<ChaosEdge>,
    startNode: String,
    targetNode: String,
    vehicle: String,
): DijkstraResult? {
    if (startNode == targetNode) {
        return DijkstraResult(edgeIds = emptyList(), totalDurationMins = 0L)
    }

    val allowedModes = allowedEdgeTypes(vehicle)
    val filteredEdges = edges.filter { edge ->
        val edgeType = normalizeEdgeType(edge.type)
        allowedModes.contains(edgeType) && !(edge.isFlooded && edgeType == "road")
    }

    if (filteredEdges.isEmpty()) {
        return null
    }

    val adjacency = filteredEdges.groupBy { it.source }
    val nodes = filteredEdges.flatMap { listOf(it.source, it.target) }.toSet()
    if (!nodes.contains(startNode) || !nodes.contains(targetNode)) {
        return null
    }

    val distances = nodes.associateWith { Long.MAX_VALUE }.toMutableMap()
    val previousNode = mutableMapOf<String, String>()
    val previousEdge = mutableMapOf<String, String>()
    val unvisited = nodes.toMutableSet()
    distances[startNode] = 0L

    while (unvisited.isNotEmpty()) {
        val current = unvisited.minByOrNull { distances[it] ?: Long.MAX_VALUE } ?: break
        val currentDistance = distances[current] ?: Long.MAX_VALUE
        if (currentDistance == Long.MAX_VALUE) {
            break
        }
        if (current == targetNode) {
            break
        }

        unvisited.remove(current)
        val neighbors = adjacency[current].orEmpty()
        neighbors.forEach { edge ->
            if (!unvisited.contains(edge.target)) {
                return@forEach
            }

            val alt = currentDistance + edge.baseWeightMins
            if (alt < (distances[edge.target] ?: Long.MAX_VALUE)) {
                distances[edge.target] = alt
                previousNode[edge.target] = current
                previousEdge[edge.target] = edge.id
            }
        }
    }

    val totalDuration = distances[targetNode] ?: Long.MAX_VALUE
    if (totalDuration == Long.MAX_VALUE) {
        return null
    }

    val pathEdges = mutableListOf<String>()
    var cursor = targetNode
    while (cursor != startNode) {
        val edgeId = previousEdge[cursor] ?: return null
        val prevNode = previousNode[cursor] ?: return null
        pathEdges.add(edgeId)
        cursor = prevNode
    }

    pathEdges.reverse()
    return DijkstraResult(edgeIds = pathEdges, totalDurationMins = totalDuration)
}

private fun allowedEdgeTypes(vehicle: String): Set<String> {
    return when (vehicle.uppercase()) {
        "TRUCK" -> setOf("road")
        "SPEEDBOAT" -> setOf("waterway")
        "DRONE" -> setOf("airway")
        else -> setOf("road")
    }
}

private fun normalizeEdgeType(rawType: String): String {
    return when (rawType.lowercase()) {
        "river", "water", "waterway" -> "waterway"
        "road" -> "road"
        "air", "airway" -> "airway"
        else -> rawType.lowercase()
    }
}
