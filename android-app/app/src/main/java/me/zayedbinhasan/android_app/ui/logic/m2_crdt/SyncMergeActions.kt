package me.zayedbinhasan.android_app.ui.logic.m2_crdt

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_PEER_ID
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.logic.core.changedFieldsJson
import me.zayedbinhasan.android_app.ui.models.IncomingMutation

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
        val strategy = mergeStrategyForField(mutation.fieldName)

        when (mutation.fieldName) {
            "status" -> {
                val remoteStatus = mutation.remoteValue
                if (mutation.timestamp > local.updated_at) {
                    repository.updateDeliveryStatus(local.task_id, remoteStatus, mutation.timestamp)
                    appendMutation(
                        repository = repository,
                        entityType = "delivery",
                        entityId = local.task_id,
                        operationType = "APPLY_REMOTE_STATUS",
                        changedFieldsJson = changedFieldsJson(
                            mapOf(
                                "field" to "status",
                                "local" to local.status,
                                "remote" to remoteStatus,
                                "strategy" to strategy,
                                "applied" to remoteStatus,
                            ),
                        ),
                    )
                } else if (mutation.timestamp == local.updated_at) {
                    val chosen = maxOf(local.status, remoteStatus)
                    repository.updateDeliveryStatus(local.task_id, chosen, mutation.timestamp)
                    appendMutation(
                        repository = repository,
                        entityType = "delivery",
                        entityId = local.task_id,
                        operationType = "APPLY_REMOTE_TIE_BREAK",
                        changedFieldsJson = changedFieldsJson(
                            mapOf(
                                "field" to "status",
                                "local" to local.status,
                                "remote" to remoteStatus,
                                "strategy" to strategy,
                                "applied" to chosen,
                            ),
                        ),
                    )
                }
            }
            "quantity" -> {
                val delta = mutation.remoteValue.toLongOrNull() ?: return@forEach
                val merged = (local.quantity + delta).coerceAtLeast(0)
                repository.updateDeliveryQuantity(local.task_id, merged, mutation.timestamp)
                appendMutation(
                    repository = repository,
                    entityType = "delivery",
                    entityId = local.task_id,
                    operationType = "APPLY_REMOTE_QUANTITY",
                    changedFieldsJson = changedFieldsJson(
                        mapOf(
                            "field" to "quantity",
                            "local" to local.quantity.toString(),
                            "remote_delta" to delta.toString(),
                            "strategy" to strategy,
                            "applied" to merged.toString(),
                        ),
                    ),
                )
            }
            "assigned_driver_id" -> {
                val localAssignee = local.assigned_driver_id
                if (localAssignee == null || localAssignee == mutation.remoteValue) {
                    repository.updateDeliveryAssignee(local.task_id, mutation.remoteValue, mutation.timestamp)
                    appendMutation(
                        repository = repository,
                        entityType = "delivery",
                        entityId = local.task_id,
                        operationType = "APPLY_REMOTE_ASSIGNMENT",
                        changedFieldsJson = changedFieldsJson(
                            mapOf(
                                "field" to "assigned_driver_id",
                                "local" to (localAssignee ?: "(null)"),
                                "remote" to mutation.remoteValue,
                                "strategy" to strategy,
                                "applied" to mutation.remoteValue,
                            ),
                        ),
                    )
                } else {
                    createConflict(
                        repository = repository,
                        entityId = local.task_id,
                        fieldName = mutation.fieldName,
                        localValue = localAssignee,
                        remoteValue = mutation.remoteValue,
                        mergeStrategy = strategy,
                    )
                }
            }
        }
    }
}
