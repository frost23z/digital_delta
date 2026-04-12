package me.zayedbinhasan.android_app.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
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
import java.security.MessageDigest
import java.util.UUID

internal fun insertDemoUser(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val userId = "user-${UUID.randomUUID().toString().take(8)}"
    repository.upsertUser(
        userId = userId,
        displayName = "Field Volunteer",
        role = "FIELD_VOLUNTEER",
        publicKey = "pk_demo_$userId",
        active = true,
        createdAt = now,
        updatedAt = now,
    )
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "UPSERT")
}

internal fun deleteUser(repository: LocalRepository, userId: String) {
    repository.deleteUserById(userId)
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "DELETE")
}

internal fun insertDemoDelivery(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val deliveryId = "task-${UUID.randomUUID().toString().take(8)}"
    repository.upsertDelivery(
        taskId = deliveryId,
        supplyId = "medical-kit-a",
        quantity = 25,
        originId = "warehouse-01",
        destinationId = "camp-03",
        priority = "P1_HIGH",
        deadlineTimestamp = now + 3_600_000,
        assignedDriverId = "driver-07",
        status = "PENDING",
        updatedAt = now,
    )
    appendMutation(repository, entityType = "delivery", entityId = deliveryId, operationType = "UPSERT")
}

internal fun deleteDelivery(repository: LocalRepository, taskId: String) {
    repository.deleteDeliveryById(taskId)
    appendMutation(repository, entityType = "delivery", entityId = taskId, operationType = "DELETE")
}

internal fun insertDemoRoute(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val routeId = "route-${UUID.randomUUID().toString().take(8)}"
    repository.upsertRoute(
        routeId = routeId,
        edgeIdsJson = "edge-1,edge-2,edge-9",
        totalDurationMins = 42,
        vehicle = "TRUCK",
        etaTimestamp = now + 2_520_000,
        reasonCode = "NORMAL_FLOW",
        updatedAt = now,
    )
    appendMutation(repository, entityType = "route", entityId = routeId, operationType = "UPSERT")
}

internal fun deleteRoute(repository: LocalRepository, routeId: String) {
    repository.deleteRouteById(routeId)
    appendMutation(repository, entityType = "route", entityId = routeId, operationType = "DELETE")
}

internal data class PodHandshakeResult(
    val accepted: Boolean,
    val status: String,
    val nonce: Long?,
)

internal fun createSignedPodHandshake(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    deliveryId: String,
    senderUserId: String,
    recipientUserId: String,
    replayNonce: Long?,
    tamperSignature: Boolean,
): PodHandshakeResult {
    val timestamp = System.currentTimeMillis()
    val nonce = replayNonce ?: timestamp

    if (repository.receiptByNonce(nonce) != null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "nonce-$nonce",
            operationType = "POD_NONCE_REPLAY_REJECTED",
            changedFieldsJson = JSONObject().apply {
                put("nonce", nonce)
                put("delivery_id", deliveryId)
            }.toString(),
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_NONCE_REPLAY_REJECTED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "nonce-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "NONCE_REPLAY_REJECTED",
            nonce = nonce,
        )
    }

    val canonicalPayload = canonicalPodPayload(
        deliveryId = deliveryId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        nonce = nonce,
        timestamp = timestamp,
    )
    val payloadHash = sha256Hex(canonicalPayload)

    val generatedSignature = authManager.signPayloadHashForUser(senderUserId, payloadHash)
    if (generatedSignature == null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "signing-$nonce",
            operationType = "POD_SIGNATURE_CREATE_FAILED",
            changedFieldsJson = "{}",
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_SIGNATURE_CREATE_FAILED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "signing-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SIGNATURE_CREATE_FAILED",
            nonce = nonce,
        )
    }

    val senderSignature = if (tamperSignature) {
        tamperBase64Signature(generatedSignature)
    } else {
        generatedSignature
    }

    val senderPublicKey = repository.userById(senderUserId)?.public_key
    if (senderPublicKey.isNullOrBlank()) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "pubkey-$senderUserId",
            operationType = "POD_VERIFY_PUBLIC_KEY_MISSING",
            changedFieldsJson = "{}",
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_VERIFY_PUBLIC_KEY_MISSING",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "pubkey-$senderUserId",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SENDER_PUBLIC_KEY_MISSING",
            nonce = nonce,
        )
    }

    val verified = authManager.verifyPayloadHashSignature(
        publicKeyBase64 = senderPublicKey,
        payloadHash = payloadHash,
        signatureBase64 = senderSignature,
    )
    if (!verified) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "verify-$nonce",
            operationType = "POD_SIGNATURE_VERIFICATION_FAILED",
            changedFieldsJson = JSONObject().apply {
                put("nonce", nonce)
                put("delivery_id", deliveryId)
                put("tampered", tamperSignature)
            }.toString(),
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_SIGNATURE_VERIFICATION_FAILED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "verify-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SIGNATURE_VERIFICATION_FAILED",
            nonce = nonce,
        )
    }

    val receiptId = "receipt-${UUID.randomUUID().toString().take(8)}"
    repository.upsertReceipt(
        receiptId = receiptId,
        deliveryId = deliveryId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        payloadHash = payloadHash,
        nonce = nonce,
        senderSignature = senderSignature,
        recipientSignature = "RECIPIENT_ACK_PENDING",
        verified = true,
        verifiedAt = timestamp,
    )

    val mutationDetails = JSONObject().apply {
        put("delivery_id", deliveryId)
        put("sender_user_id", senderUserId)
        put("recipient_user_id", recipientUserId)
        put("nonce", nonce)
        put("timestamp", timestamp)
        put("verified", true)
    }.toString()

    appendMutation(
        repository = repository,
        entityType = "receipt",
        entityId = receiptId,
        operationType = "POD_HANDSHAKE_VERIFIED",
        changedFieldsJson = mutationDetails,
        actorId = senderUserId,
    )
    authManager.recordAuditEvent(
        eventType = "POD_HANDSHAKE_VERIFIED",
        actorId = senderUserId,
        entityType = "receipt",
        entityId = receiptId,
    )

    return PodHandshakeResult(
        accepted = true,
        status = "HANDSHAKE_ACCEPTED",
        nonce = nonce,
    )
}

internal fun deleteReceipt(repository: LocalRepository, receiptId: String) {
    repository.deleteReceiptById(receiptId)
    appendMutation(repository, entityType = "receipt", entityId = receiptId, operationType = "DELETE")
}

internal fun appendMutation(
    repository: LocalRepository,
    entityType: String,
    entityId: String,
    operationType: String,
    changedFieldsJson: String = "{}",
    actorId: String = "ui_user",
) {
    val deviceId = "android_client"
    val nextCounter = repository.localDeviceMutationCount(deviceId) + 1
    val vectorClockJson = "{\"$deviceId\":$nextCounter}"

    repository.insertMutation(
        mutationId = "mut-${UUID.randomUUID().toString().take(12)}",
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        changedFieldsJson = changedFieldsJson,
        vectorClockJson = vectorClockJson,
        actorId = actorId,
        deviceId = deviceId,
        mutationTimestamp = System.currentTimeMillis(),
        synced = false,
    )
}

internal fun canonicalPodPayload(
    deliveryId: String,
    senderUserId: String,
    recipientUserId: String,
    nonce: Long,
    timestamp: Long,
): String {
    return listOf(
        "delivery_id=$deliveryId",
        "sender_user_id=$senderUserId",
        "recipient_user_id=$recipientUserId",
        "nonce=$nonce",
        "timestamp=$timestamp",
    ).joinToString(separator = "|")
}

internal fun tamperBase64Signature(base64Signature: String): String {
    if (base64Signature.isEmpty()) {
        return base64Signature
    }

    val lastChar = base64Signature.last()
    val replacement = if (lastChar == 'A') 'B' else 'A'
    return base64Signature.dropLast(1) + replacement
}

internal fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun simulateRemoteStatusMerge(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: return
    val remoteStatus = "IN_TRANSIT"
    val remoteTimestamp = System.currentTimeMillis()

    when {
        remoteTimestamp > delivery.updated_at -> {
            repository.updateDeliveryStatus(delivery.task_id, remoteStatus, remoteTimestamp)
            appendMutation(repository, entityType = "delivery", entityId = delivery.task_id, operationType = "MERGE_REMOTE_STATUS")
        }
        remoteTimestamp == delivery.updated_at -> {
            val chosenStatus = maxOf(delivery.status, remoteStatus)
            repository.updateDeliveryStatus(delivery.task_id, chosenStatus, remoteTimestamp)
            appendMutation(repository, entityType = "delivery", entityId = delivery.task_id, operationType = "MERGE_TIE_BREAK")
        }
    }
}

internal fun simulateRemoteQuantityMerge(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: return
    val remoteDelta = 5L
    val mergedQuantity = (delivery.quantity + remoteDelta).coerceAtLeast(0)
    repository.updateDeliveryQuantity(delivery.task_id, mergedQuantity, System.currentTimeMillis())
    appendMutation(repository, entityType = "delivery", entityId = delivery.task_id, operationType = "MERGE_ADDITIVE")
}

internal fun simulateRemoteOwnershipConflict(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: run {
        insertDemoDelivery(repository)
        repository.firstDeliveryOrNull()
    } ?: return

    val remoteAssignee = "driver-remote"
    val localAssignee = delivery.assigned_driver_id

    if (localAssignee == null || localAssignee == remoteAssignee) {
        repository.updateDeliveryAssignee(delivery.task_id, remoteAssignee, System.currentTimeMillis())
        appendMutation(repository, entityType = "delivery", entityId = delivery.task_id, operationType = "MERGE_ASSIGNMENT")
        return
    }

    createConflict(
        repository = repository,
        entityId = delivery.task_id,
        localValue = localAssignee,
        remoteValue = remoteAssignee,
        mergeStrategy = "MANUAL_OWNERSHIP",
    )
}

internal fun createConflict(
    repository: LocalRepository,
    entityId: String,
    localValue: String?,
    remoteValue: String?,
    mergeStrategy: String,
) {
    val now = System.currentTimeMillis()
    val conflictId = "conf-${UUID.randomUUID().toString().take(12)}"
    repository.insertConflict(
        conflictId = conflictId,
        entityType = "delivery",
        entityId = entityId,
        fieldName = "assigned_driver_id",
        localValue = localValue,
        remoteValue = remoteValue,
        mergeStrategy = mergeStrategy,
        manualRequired = true,
        status = "OPEN",
        createdAt = now,
        resolvedAt = null,
        resolution = null,
    )
    appendMutation(repository, entityType = "conflict", entityId = conflictId, operationType = "OPEN")
}

internal fun resolveConflictAction(
    repository: LocalRepository,
    conflict: ConflictUi,
    action: String,
) {
    val now = System.currentTimeMillis()
    var deliveryUpdated = false

    if (conflict.entityType == "delivery") {
        when (conflict.fieldName) {
            "assigned_driver_id" -> {
                if (action == "ACCEPT_REMOTE") {
                    repository.updateDeliveryAssignee(conflict.entityId, conflict.remoteValue, now)
                    deliveryUpdated = true
                }
                if (action == "MERGE_MANUAL") {
                    val mergedAssignee = listOfNotNull(conflict.localValue, conflict.remoteValue)
                        .distinct()
                        .joinToString(separator = " | ")
                    repository.updateDeliveryAssignee(conflict.entityId, mergedAssignee, now)
                    deliveryUpdated = true
                }
            }
            "status" -> {
                if (action == "ACCEPT_REMOTE" && conflict.remoteValue != null) {
                    repository.updateDeliveryStatus(conflict.entityId, conflict.remoteValue, now)
                    deliveryUpdated = true
                }
            }
            "quantity" -> {
                if (action == "ACCEPT_REMOTE") {
                    val remoteQuantity = conflict.remoteValue?.toLongOrNull() ?: return
                    repository.updateDeliveryQuantity(conflict.entityId, remoteQuantity, now)
                    deliveryUpdated = true
                }
            }
        }
    }

    if (deliveryUpdated) {
        appendMutation(repository, entityType = "delivery", entityId = conflict.entityId, operationType = "MERGE_RESOLVE")
    }

    repository.resolveConflict(
        conflictId = conflict.conflictId,
        resolvedAt = now,
        resolution = action,
    )
    appendMutation(repository, entityType = "conflict", entityId = conflict.conflictId, operationType = "RESOLVE")
}

internal fun simulateSyncWithPeer(
    repository: LocalRepository,
    appliedMutationCount: Int,
) {
    val peerId = DEFAULT_SYNC_PEER_ID
    val now = System.currentTimeMillis()
    val existingCheckpoint = repository.syncCheckpointByPeer(peerId)
    val latestMutationTimestamp = repository.latestMutationTimestampNow() ?: now
    val nextCounter = (existingCheckpoint?.last_seen_counter ?: 0L) + appliedMutationCount.toLong()

    if (appliedMutationCount > 0) {
        repository.markAllMutationsSynced()
    }

    repository.upsertSyncCheckpoint(
        peerId = peerId,
        lastSeenCounter = nextCounter,
        lastSyncTimestamp = latestMutationTimestamp,
        updatedAt = now,
    )
    appendMutation(repository, entityType = "sync_checkpoint", entityId = peerId, operationType = "UPSERT")
}

internal fun applyIncomingMutationBatch(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: run {
        insertDemoDelivery(repository)
        repository.firstDeliveryOrNull()
    } ?: return

    val incoming = listOf(
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "status",
            remoteValue = "IN_TRANSIT",
            mergeStrategy = "LWW",
            timestamp = System.currentTimeMillis(),
        ),
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "quantity",
            remoteValue = "5",
            mergeStrategy = "ADDITIVE",
            timestamp = System.currentTimeMillis(),
        ),
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "assigned_driver_id",
            remoteValue = "driver-remote",
            mergeStrategy = "MANUAL_OWNERSHIP",
            timestamp = System.currentTimeMillis(),
        ),
    )

    applyIncomingMutations(repository, incoming)
}

internal fun applyIncomingMutations(repository: LocalRepository, incoming: List<IncomingMutation>) {
    incoming.forEach { mutation ->
        if (mutation.entityType != "delivery") {
            return@forEach
        }

        val local = repository.deliveryById(mutation.entityId) ?: return@forEach

        when (mutation.fieldName) {
            "status" -> {
                val remoteStatus = mutation.remoteValue
                if (mutation.timestamp > local.updated_at) {
                    repository.updateDeliveryStatus(local.task_id, remoteStatus, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_STATUS")
                } else if (mutation.timestamp == local.updated_at) {
                    val chosen = maxOf(local.status, remoteStatus)
                    repository.updateDeliveryStatus(local.task_id, chosen, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_TIE_BREAK")
                }
            }
            "quantity" -> {
                val delta = mutation.remoteValue.toLongOrNull() ?: return@forEach
                val merged = (local.quantity + delta).coerceAtLeast(0)
                repository.updateDeliveryQuantity(local.task_id, merged, mutation.timestamp)
                appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_QUANTITY")
            }
            "assigned_driver_id" -> {
                val localAssignee = local.assigned_driver_id
                if (localAssignee == null || localAssignee == mutation.remoteValue) {
                    repository.updateDeliveryAssignee(local.task_id, mutation.remoteValue, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_ASSIGNMENT")
                } else {
                    createConflict(
                        repository = repository,
                        entityId = local.task_id,
                        localValue = localAssignee,
                        remoteValue = mutation.remoteValue,
                        mergeStrategy = mutation.mergeStrategy,
                    )
                }
            }
        }
    }
}

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

internal data class UserUi(
    val userId: String,
    val displayName: String,
    val role: String,
    val active: Boolean,
)

internal data class DeliveryUi(
    val taskId: String,
    val quantity: Long,
    val originId: String,
    val destinationId: String,
    val status: String,
    val assignedDriverId: String?,
)

internal data class DeliveryFullUi(
    val taskId: String,
    val supplyId: String,
    val quantity: Long,
    val originId: String,
    val destinationId: String,
    val priority: String,
    val deadlineTimestamp: Long,
    val assignedDriverId: String?,
    val status: String,
    val updatedAt: Long,
)

internal data class RouteUi(
    val routeId: String,
    val vehicle: String,
    val reasonCode: String,
)

internal data class RouteFullUi(
    val routeId: String,
    val edgeIds: String,
    val totalDurationMins: Long,
    val vehicle: String,
    val etaTimestamp: Long,
    val reasonCode: String,
)

internal data class ReceiptUi(
    val receiptId: String,
    val deliveryId: String,
    val verified: Boolean,
)

internal data class ReceiptFullUi(
    val receiptId: String,
    val deliveryId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val nonce: Long,
    val verified: Boolean,
    val verifiedAt: Long?,
)

internal data class MutationUi(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val deviceId: String,
    val mutationTimestamp: Long,
)

internal data class IncomingMutation(
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val remoteValue: String,
    val mergeStrategy: String,
    val timestamp: Long,
)

internal data class SyncCheckpointUi(
    val peerId: String,
    val lastSeenCounter: Long,
    val lastSyncTimestamp: Long,
    val updatedAt: Long,
)

internal data class ConflictUi(
    val conflictId: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val localValue: String?,
    val remoteValue: String?,
    val mergeStrategy: String,
    val manualRequired: Boolean,
    val createdAt: Long,
)

internal fun canManageRouteActions(role: String): Boolean {
    return role == "SUPPLY_MANAGER" || role == "CAMP_COMMANDER" || role == "SYNC_ADMIN"
}