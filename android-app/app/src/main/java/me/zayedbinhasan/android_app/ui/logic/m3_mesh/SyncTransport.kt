package me.zayedbinhasan.android_app.ui.logic.m3_mesh

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.applyIncomingMutations
import me.zayedbinhasan.android_app.ui.models.IncomingMutation
import me.zayedbinhasan.data.Mutation_logs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class ServerDeltaSyncResult(
    val syncedMutationIds: List<String>,
    val incomingMutations: List<IncomingMutation>,
    val updatedCheckpoint: Map<String, Long>,
    val message: String,
)

internal data class PeerTransferStatus(
    val peerId: String,
    val messageId: String,
    val ttl: Int,
    val seenNodes: List<String>,
    val attemptCount: Int,
    val success: Boolean,
    val detail: String,
)

internal data class PeerDeltaSyncResult(
    val transferStatuses: List<PeerTransferStatus>,
    val syncedMutationIds: List<String>,
    val incomingMutations: List<IncomingMutation>,
)

internal data class PeerRelayEnvelope(
    val messageId: String,
    val ttl: Int,
    val seenNodes: List<String>,
    val attemptCount: Int,
    val payload: JSONObject,
)

internal fun buildDeltaSyncRequestJson(
    nodeId: String,
    checkpointCounter: Long,
    outgoingMutations: List<Mutation_logs>,
): JSONObject {
    return JSONObject().apply {
        put("node_id", nodeId)
        put("checkpoint", JSONObject().apply {
            put(nodeId, checkpointCounter)
        })

        val outgoingArray = JSONArray()
        outgoingMutations.forEach { mutation ->
            val vectorClock = parseLongMap(mutation.vector_clock_json)
            outgoingArray.put(
                JSONObject().apply {
                    put("mutation_id", mutation.mutation_id)
                    put("entity_type", mutation.entity_type)
                    put("entity_id", mutation.entity_id)
                    put("changed_fields_json", mutation.changed_fields_json)
                    put("actor_id", mutation.actor_id)
                    put("timestamp", mutation.mutation_timestamp)
                    put("device_id", mutation.device_id)
                    put("vector_clock", JSONObject(vectorClock))
                },
            )
        }
        put("outgoing_mutations", outgoingArray)
    }
}

internal fun performPeerDeltaSyncLan(
    host: String,
    port: Int,
    nodeId: String,
    checkpointCounter: Long,
    outgoingMutations: List<Mutation_logs>,
): PeerDeltaSyncResult {
    val payload = buildDeltaSyncRequestJson(nodeId, checkpointCounter, outgoingMutations)
    val outgoingCount = payload.optJSONArray("outgoing_mutations")?.length() ?: 0

    val messageId = "peer-msg-${System.currentTimeMillis()}"
    var ttl = 3
    var attemptCount = 0
    val seenNodes = mutableListOf(nodeId)
    var transferStatus = PeerTransferStatus(
        peerId = "$host:$port",
        messageId = messageId,
        ttl = ttl,
        seenNodes = seenNodes,
        attemptCount = attemptCount,
        success = false,
        detail = "not attempted",
    )

    while (attemptCount < 2 && ttl > 0) {
        attemptCount += 1
        ttl -= 1

        val result = runCatching {
            val envelope = JSONObject().apply {
                put("message_id", messageId)
                put("ttl", ttl)
                put("seen_nodes", JSONArray(seenNodes))
                put("attempt_count", attemptCount)
                put("payload", payload)
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4000)
                socket.soTimeout = 6000

                socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(envelope.toString())
                    writer.write("\n")
                    writer.flush()
                }

                socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readLine().orEmpty()
                }
            }
        }

        if (result.isSuccess) {
            val response = JSONObject(result.getOrDefault("{}"))
            val relay = parsePeerRelayEnvelope(response)
            val incomingMutations = parseIncomingMutationsArray(relay.payload.optJSONArray("incoming_mutations") ?: JSONArray())
            val syncedIds = parseStringList(relay.payload.optJSONArray("synced_mutation_ids") ?: JSONArray())

            transferStatus = PeerTransferStatus(
                peerId = "$host:$port",
                messageId = messageId,
                ttl = relay.ttl,
                seenNodes = relay.seenNodes,
                attemptCount = relay.attemptCount,
                success = true,
                detail = "delivered $outgoingCount mutation payload(s)",
            )

            return PeerDeltaSyncResult(
                transferStatuses = listOf(transferStatus),
                syncedMutationIds = syncedIds,
                incomingMutations = incomingMutations,
            )
        }

        transferStatus = PeerTransferStatus(
            peerId = "$host:$port",
            messageId = messageId,
            ttl = ttl,
            seenNodes = seenNodes,
            attemptCount = attemptCount,
            success = false,
            detail = result.exceptionOrNull()?.message ?: "connection failed",
        )
    }

    return PeerDeltaSyncResult(
        transferStatuses = listOf(transferStatus),
        syncedMutationIds = emptyList(),
        incomingMutations = emptyList(),
    )
}

internal fun receivePeerDeltaSyncOnce(
    repository: LocalRepository,
    nodeId: String,
    listenPort: Int,
): PeerTransferStatus {
    return runCatching {
        ServerSocket(listenPort).use { serverSocket ->
            serverSocket.soTimeout = 15_000

            serverSocket.accept().use { socket ->
                socket.soTimeout = 6_000

                val rawRequest = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readLine().orEmpty()
                }
                val requestRelay = parsePeerRelayEnvelope(JSONObject(rawRequest))

                val requesterId = requestRelay.payload.optString("node_id", "peer-client")
                val outgoingArray = requestRelay.payload.optJSONArray("outgoing_mutations") ?: JSONArray()
                val incomingForThisDevice = parseIncomingMutationsArray(outgoingArray)
                if (incomingForThisDevice.isNotEmpty()) {
                    applyIncomingMutations(repository, incomingForThisDevice)
                }

                val localPending = runBlocking { repository.observePendingMutations().first() }
                val responsePayload = JSONObject().apply {
                    put("message", "peer sync completed")
                    put("synced_mutation_ids", extractMutationIds(outgoingArray))
                    put("incoming_mutations", mutationLogsToJsonArray(localPending))
                    put("updated_checkpoint", JSONObject().apply {
                        put(nodeId, repository.localDeviceMutationCount(nodeId))
                    })
                }

                if (localPending.isNotEmpty()) {
                    repository.markAllMutationsSynced()
                }

                val now = System.currentTimeMillis()
                repository.upsertSyncCheckpoint(
                    peerId = requesterId,
                    lastSeenCounter = outgoingArray.length().toLong(),
                    lastSyncTimestamp = now,
                    updatedAt = now,
                )
                appendMutation(repository, entityType = "sync_checkpoint", entityId = requesterId, operationType = "UPSERT")

                val responseRelay = JSONObject().apply {
                    put("message_id", "${requestRelay.messageId}-ack")
                    put("ttl", maxOf(0, requestRelay.ttl - 1))
                    put("seen_nodes", JSONArray(requestRelay.seenNodes + nodeId))
                    put("attempt_count", requestRelay.attemptCount + 1)
                    put("payload", responsePayload)
                }

                socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(responseRelay.toString())
                    writer.write("\n")
                    writer.flush()
                }

                PeerTransferStatus(
                    peerId = requesterId,
                    messageId = requestRelay.messageId,
                    ttl = requestRelay.ttl,
                    seenNodes = requestRelay.seenNodes,
                    attemptCount = requestRelay.attemptCount,
                    success = true,
                    detail = "received ${outgoingArray.length()} payload(s), responded with ${localPending.size}",
                )
            }
        }
    }.getOrElse { error ->
        val message = if (error is SocketTimeoutException) {
            "listener timeout waiting for peer"
        } else {
            error.message ?: "listener failed"
        }

        PeerTransferStatus(
            peerId = "listener:$listenPort",
            messageId = "peer-listen-${System.currentTimeMillis()}",
            ttl = 0,
            seenNodes = listOf(nodeId),
            attemptCount = 1,
            success = false,
            detail = message,
        )
    }
}

internal fun parsePeerRelayEnvelope(raw: JSONObject): PeerRelayEnvelope {
    val seenNodes = parseStringList(raw.optJSONArray("seen_nodes") ?: JSONArray())
    return PeerRelayEnvelope(
        messageId = raw.optString("message_id"),
        ttl = raw.optInt("ttl", 0),
        seenNodes = seenNodes.ifEmpty { listOf("unknown") },
        attemptCount = raw.optInt("attempt_count", 0),
        payload = raw.optJSONObject("payload") ?: JSONObject(),
    )
}

internal fun parseStringList(array: JSONArray): List<String> {
    val result = mutableListOf<String>()
    for (index in 0 until array.length()) {
        val value = array.optString(index)
        if (value.isNotBlank()) {
            result += value
        }
    }
    return result
}

internal fun extractMutationIds(mutationsArray: JSONArray): JSONArray {
    val ids = JSONArray()
    for (index in 0 until mutationsArray.length()) {
        val mutationJson = mutationsArray.optJSONObject(index) ?: continue
        val mutationId = mutationJson.optString("mutation_id")
        if (mutationId.isNotBlank()) {
            ids.put(mutationId)
        }
    }
    return ids
}

internal fun mutationLogsToJsonArray(mutations: List<Mutation_logs>): JSONArray {
    val payload = JSONArray()
    mutations.forEach { mutation ->
        payload.put(
            JSONObject().apply {
                put("mutation_id", mutation.mutation_id)
                put("entity_type", mutation.entity_type)
                put("entity_id", mutation.entity_id)
                put("changed_fields", JSONObject(parseStringMap(mutation.changed_fields_json)))
                put("actor_id", mutation.actor_id)
                put("timestamp", mutation.mutation_timestamp)
                put("device_id", mutation.device_id)
                put("vector_clock", JSONObject(parseLongMap(mutation.vector_clock_json)))
            },
        )
    }
    return payload
}

internal fun parseIncomingMutationsArray(mutationsArray: JSONArray): List<IncomingMutation> {
    val incomingMutations = mutableListOf<IncomingMutation>()

    for (index in 0 until mutationsArray.length()) {
        val mutationJson = mutationsArray.optJSONObject(index) ?: continue
        val entityType = mutationJson.optString("entity_type")
        val entityId = mutationJson.optString("entity_id")
        val timestamp = mutationJson.optLong("timestamp")

        val changedFields = when {
            mutationJson.has("changed_fields") -> {
                parseStringMap(mutationJson.optJSONObject("changed_fields")?.toString())
            }
            else -> {
                parseStringMap(mutationJson.optString("changed_fields_json"))
            }
        }

        changedFields.forEach { (fieldName, remoteValue) ->
            val strategy = when (fieldName) {
                "quantity" -> "ADDITIVE"
                "assigned_driver_id" -> "MANUAL_OWNERSHIP"
                else -> "LWW"
            }
            incomingMutations += IncomingMutation(
                entityType = entityType,
                entityId = entityId,
                fieldName = fieldName,
                remoteValue = remoteValue,
                mergeStrategy = strategy,
                timestamp = timestamp,
            )
        }
    }

    return incomingMutations
}

internal fun performServerDeltaSync(
    baseUrl: String,
    nodeId: String,
    checkpointCounter: Long,
    outgoingMutations: List<Mutation_logs>,
): ServerDeltaSyncResult? {
    return runCatching {
        val requestJson = buildDeltaSyncRequestJson(nodeId, checkpointCounter, outgoingMutations)

        val url = URL("$baseUrl/api/sync/delta")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 6000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(requestJson.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: ""
        connection.disconnect()

        if (responseCode !in 200..299 || responseBody.isBlank()) {
            return null
        }

        val responseJson = JSONObject(responseBody)
        val syncedIds = mutableListOf<String>()
        val syncedIdsArray = responseJson.optJSONArray("synced_mutation_ids") ?: JSONArray()
        for (i in 0 until syncedIdsArray.length()) {
            syncedIds += syncedIdsArray.optString(i)
        }

        val updatedCheckpoint = parseLongMap(responseJson.optJSONObject("updated_checkpoint")?.toString())

        val incomingMutations = mutableListOf<IncomingMutation>()
        val incomingArray = responseJson.optJSONArray("incoming_mutations") ?: JSONArray()
        for (i in 0 until incomingArray.length()) {
            val mutationJson = incomingArray.optJSONObject(i) ?: continue
            val entityType = mutationJson.optString("entity_type")
            val entityId = mutationJson.optString("entity_id")
            val timestamp = mutationJson.optLong("timestamp")
            val changedFields = parseStringMap(mutationJson.optJSONObject("changed_fields")?.toString())

            changedFields.forEach { (fieldName, remoteValue) ->
                val strategy = when (fieldName) {
                    "quantity" -> "ADDITIVE"
                    "assigned_driver_id" -> "MANUAL_OWNERSHIP"
                    else -> "LWW"
                }
                incomingMutations += IncomingMutation(
                    entityType = entityType,
                    entityId = entityId,
                    fieldName = fieldName,
                    remoteValue = remoteValue,
                    mergeStrategy = strategy,
                    timestamp = timestamp,
                )
            }
        }

        ServerDeltaSyncResult(
            syncedMutationIds = syncedIds,
            incomingMutations = incomingMutations,
            updatedCheckpoint = updatedCheckpoint,
            message = responseJson.optString("message"),
        )
    }.getOrNull()
}

internal fun parseLongMap(rawJson: String?): Map<String, Long> {
    if (rawJson.isNullOrBlank()) {
        return emptyMap()
    }

    return runCatching {
        val jsonObject = JSONObject(rawJson)
        val keys = jsonObject.keys()
        val parsed = mutableMapOf<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            parsed[key] = jsonObject.optLong(key)
        }
        parsed
    }.getOrDefault(emptyMap())
}

internal fun parseStringMap(rawJson: String?): Map<String, String> {
    if (rawJson.isNullOrBlank()) {
        return emptyMap()
    }

    return runCatching {
        val jsonObject = JSONObject(rawJson)
        val keys = jsonObject.keys()
        val parsed = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            parsed[key] = jsonObject.optString(key)
        }
        parsed
    }.getOrDefault(emptyMap())
}
