package me.zayedbinhasan.android_app.ui.logic

import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_PEER_ID
import me.zayedbinhasan.android_app.ui.models.ConflictUi
import me.zayedbinhasan.android_app.ui.models.IncomingMutation
import org.json.JSONObject
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

internal fun canManageRouteActions(role: String): Boolean {
    return role == "SUPPLY_MANAGER" || role == "CAMP_COMMANDER" || role == "SYNC_ADMIN"
}