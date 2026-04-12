package me.zayedbinhasan.android_app.ui.logic.m2_crdt

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.models.ConflictUi
import java.util.UUID

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
