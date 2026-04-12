package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.DEFAULT_CHAOS_HTTP_BASE_URL
import me.zayedbinhasan.android_app.ui.logic.m4_routing.canManageRouteActions
import me.zayedbinhasan.android_app.ui.logic.m4_routing.deleteRoute
import me.zayedbinhasan.android_app.ui.logic.m4_routing.insertDemoRoute
import me.zayedbinhasan.android_app.ui.logic.m4_routing.refreshRoutesFromChaos
import me.zayedbinhasan.android_app.ui.models.RouteFullUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RoutesScreen(
    repository: LocalRepository,
    activeRole: String,
) {
    val coroutineScope = rememberCoroutineScope()
    val routesRaw by remember(repository) {
        repository.observeRoutes()
    }.collectAsState(initial = emptyList())

    var chaosBaseUrl by rememberSaveable { mutableStateOf(DEFAULT_CHAOS_HTTP_BASE_URL) }
    var routeEngineBusy by rememberSaveable { mutableStateOf(false) }
    var routeEngineMessage by rememberSaveable { mutableStateOf("Idle") }
    var triageSummary by rememberSaveable { mutableStateOf("No triage actions yet") }
    var lastRecomputedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var autoPollEnabled by rememberSaveable { mutableStateOf(false) }

    val canManageRoutes = canManageRouteActions(activeRole)

    val routes = routesRaw.map { row ->
        RouteFullUi(
            routeId = row.route_id,
            edgeIds = row.edge_ids_json,
            totalDurationMins = row.total_duration_mins,
            vehicle = row.vehicle,
            etaTimestamp = row.eta_timestamp,
            reasonCode = row.reason_code,
        )
    }

    fun applyRefreshSummary(
        success: Boolean,
        reasonCode: String,
        detail: String,
        changedRouteCount: Int,
        blockedRouteCount: Int,
        preemptedLowPriorityCount: Int,
        reroutedHighPriorityCount: Int,
        recomputedAt: Long,
    ) {
        lastRecomputedAt = recomputedAt
        routeEngineMessage = if (success) {
            "$reasonCode | changed=$changedRouteCount blocked=$blockedRouteCount"
        } else {
            "$reasonCode | $detail"
        }
        triageSummary = "P2/P3 dropped: $preemptedLowPriorityCount, P0/P1 rerouted: $reroutedHighPriorityCount"
    }

    LaunchedEffect(autoPollEnabled, chaosBaseUrl, canManageRoutes) {
        if (!autoPollEnabled || !canManageRoutes) {
            return@LaunchedEffect
        }

        while (isActive) {
            val summary = withContext(Dispatchers.IO) {
                refreshRoutesFromChaos(repository, chaosBaseUrl.trim().trimEnd('/'))
            }
            applyRefreshSummary(
                success = summary.success,
                reasonCode = summary.reasonCode,
                detail = summary.detail,
                changedRouteCount = summary.changedRouteCount,
                blockedRouteCount = summary.blockedRouteCount,
                preemptedLowPriorityCount = summary.preemptedLowPriorityCount,
                reroutedHighPriorityCount = summary.reroutedHighPriorityCount,
                recomputedAt = summary.recomputedAt,
            )
            delay(2_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routes", fontWeight = FontWeight.Bold)
        if (!canManageRoutes) {
            Text("Route management requires MANAGER/COMMANDER/ADMIN role")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("M4 + M6 Controls", fontWeight = FontWeight.Bold)
                Text("SLA table: P0<=60m, P1<=180m, P2<=360m, P3<=720m")
                Text("Breach trigger: ETA increase >= 30% or SLA exceeded")
                Text("Route Engine: $routeEngineMessage")
                Text("Triage: $triageSummary")
                Text("Last recompute: ${lastRecomputedAt ?: "Never"}")

                OutlinedTextField(
                    value = chaosBaseUrl,
                    onValueChange = { chaosBaseUrl = it },
                    label = { Text("Chaos API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(onClick = { insertDemoRoute(repository, vehicle = "TRUCK") }, enabled = canManageRoutes) {
                    Text("Create Demo Truck Route")
                }
                Button(onClick = { insertDemoRoute(repository, vehicle = "SPEEDBOAT") }, enabled = canManageRoutes) {
                    Text("Create Demo Boat Route")
                }
                Button(onClick = { insertDemoRoute(repository, vehicle = "DRONE") }, enabled = canManageRoutes) {
                    Text("Create Demo Drone Route")
                }

                Button(
                    onClick = {
                        if (routeEngineBusy) {
                            return@Button
                        }

                        routeEngineBusy = true
                        coroutineScope.launch {
                            val summary = withContext(Dispatchers.IO) {
                                refreshRoutesFromChaos(repository, chaosBaseUrl.trim().trimEnd('/'))
                            }
                            applyRefreshSummary(
                                success = summary.success,
                                reasonCode = summary.reasonCode,
                                detail = summary.detail,
                                changedRouteCount = summary.changedRouteCount,
                                blockedRouteCount = summary.blockedRouteCount,
                                preemptedLowPriorityCount = summary.preemptedLowPriorityCount,
                                reroutedHighPriorityCount = summary.reroutedHighPriorityCount,
                                recomputedAt = summary.recomputedAt,
                            )
                            routeEngineBusy = false
                        }
                    },
                    enabled = canManageRoutes && !routeEngineBusy,
                ) {
                    Text(if (routeEngineBusy) "Recomputing..." else "Recompute From Chaos Now")
                }

                Button(
                    onClick = { autoPollEnabled = !autoPollEnabled },
                    enabled = canManageRoutes,
                ) {
                    Text(if (autoPollEnabled) "Stop Auto Poll (2s)" else "Start Auto Poll (2s)")
                }
            }
        }

        HorizontalDivider()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routes, key = { it.routeId }) { route ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${route.routeId} • ${route.vehicle}", fontWeight = FontWeight.Bold)
                        Text("ETA: ${route.etaTimestamp}")
                        Text("Duration: ${route.totalDurationMins} mins")
                        Text("Reason: ${route.reasonCode}")
                        Text("Edges: ${route.edgeIds}")
                        Button(onClick = { deleteRoute(repository, route.routeId) }, enabled = canManageRoutes) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
