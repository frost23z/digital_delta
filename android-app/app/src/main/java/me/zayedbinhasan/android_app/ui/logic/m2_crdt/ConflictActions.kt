package me.zayedbinhasan.android_app.ui.logic.m2_crdt

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.logic.core.changedFieldsJson
import me.zayedbinhasan.android_app.ui.models.ConflictUi
import java.util.UUID

internal fun simulateRemoteStatusMerge(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: return
    val remoteStatus = "IN_TRANSIT"
    val remoteTimestamp = System.currentTimeMillis()

    when {
        remoteTimestamp > delivery.updated_at -> {
            repository.updateDeliveryStatus(delivery.task_id, remoteStatus, remoteTimestamp)
            appendMutation(
                repository = repository,
                entityType = "delivery",
                entityId = delivery.task_id,
                operationType = "MERGE_REMOTE_STATUS",
                changedFieldsJson = changedFieldsJson(
                    mapOf(
                        "field" to "status",
                        "local" to delivery.status,
                        "remote" to remoteStatus,
                        "strategy" to mergeStrategyForField("status"),
                        "applied" to remoteStatus,
                    ),
                ),
            )
        }
        remoteTimestamp == delivery.updated_at -> {
            val chosenStatus = maxOf(delivery.status, remoteStatus)
            repository.updateDeliveryStatus(delivery.task_id, chosenStatus, remoteTimestamp)
            appendMutation(
                repository = repository,
                entityType = "delivery",
                entityId = delivery.task_id,
                operationType = "MERGE_TIE_BREAK",
                changedFieldsJson = changedFieldsJson(
                    mapOf(
                        "field" to "status",
                        "local" to delivery.status,
                        "remote" to remoteStatus,
                        "strategy" to mergeStrategyForField("status"),
                        "applied" to chosenStatus,
                    ),
                ),
            )
        }
    }
}

internal fun simulateRemoteQuantityMerge(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: return
    val remoteDelta = 5L
    val mergedQuantity = (delivery.quantity + remoteDelta).coerceAtLeast(0)
    repository.updateDeliveryQuantity(delivery.task_id, mergedQuantity, System.currentTimeMillis())
    appendMutation(
        repository = repository,
        entityType = "delivery",
        entityId = delivery.task_id,
        operationType = "MERGE_ADDITIVE",
        changedFieldsJson = changedFieldsJson(
            mapOf(
                "field" to "quantity",
                "local" to delivery.quantity.toString(),
                "remote_delta" to remoteDelta.toString(),
                "strategy" to mergeStrategyForField("quantity"),
                "applied" to mergedQuantity.toString(),
            ),
        ),
    )
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
        appendMutation(
            repository = repository,
            entityType = "delivery",
            entityId = delivery.task_id,
            operationType = "MERGE_ASSIGNMENT",
            changedFieldsJson = changedFieldsJson(
                mapOf(
                    "field" to "assigned_driver_id",
                    "local" to (localAssignee ?: "(null)"),
                    "remote" to remoteAssignee,
                    "strategy" to mergeStrategyForField("assigned_driver_id"),
                    "applied" to remoteAssignee,
                ),
            ),
        )
        return
    }

    createConflict(
        repository = repository,
        entityId = delivery.task_id,
        fieldName = "assigned_driver_id",
        localValue = localAssignee,
        remoteValue = remoteAssignee,
        mergeStrategy = mergeStrategyForField("assigned_driver_id"),
    )
}

internal fun createConflict(
    repository: LocalRepository,
    entityId: String,
    fieldName: String,
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
        fieldName = fieldName,
        localValue = localValue,
        remoteValue = remoteValue,
        mergeStrategy = mergeStrategy,
        manualRequired = true,
        status = "OPEN",
        createdAt = now,
        resolvedAt = null,
        resolution = null,
    )
    appendMutation(
        repository = repository,
        entityType = "conflict",
        entityId = conflictId,
        operationType = "OPEN",
        changedFieldsJson = changedFieldsJson(
            mapOf(
                "field" to fieldName,
                "local" to (localValue ?: "(null)"),
                "remote" to (remoteValue ?: "(null)"),
                "strategy" to mergeStrategy,
                "status" to "OPEN",
            ),
        ),
    )
}

internal fun resolveConflictAction(
    repository: LocalRepository,
    conflict: ConflictUi,
    action: String,
) {
    val now = System.currentTimeMillis()
    var deliveryUpdated = false
    var finalValue: String? = null

    if (conflict.entityType == "delivery") {
        when (conflict.fieldName) {
            "assigned_driver_id" -> {
                if (action == "ACCEPT_LOCAL") {
                    finalValue = conflict.localValue
                }
                if (action == "ACCEPT_REMOTE") {
                    repository.updateDeliveryAssignee(conflict.entityId, conflict.remoteValue, now)
                    deliveryUpdated = true
                    finalValue = conflict.remoteValue
                }
                if (action == "MERGE_MANUAL") {
                    val mergedAssignee = listOfNotNull(conflict.localValue, conflict.remoteValue)
                        .distinct()
                        .joinToString(separator = " | ")
                    repository.updateDeliveryAssignee(conflict.entityId, mergedAssignee, now)
                    deliveryUpdated = true
                    finalValue = mergedAssignee
                }
            }
            "status" -> {
                if (action == "ACCEPT_LOCAL") {
                    finalValue = conflict.localValue
                }
                if (action == "ACCEPT_REMOTE" && conflict.remoteValue != null) {
                    repository.updateDeliveryStatus(conflict.entityId, conflict.remoteValue, now)
                    deliveryUpdated = true
                    finalValue = conflict.remoteValue
                }
            }
            "quantity" -> {
                if (action == "ACCEPT_LOCAL") {
                    finalValue = conflict.localValue
                }
                if (action == "ACCEPT_REMOTE") {
                    val remoteQuantity = conflict.remoteValue?.toLongOrNull() ?: return
                    repository.updateDeliveryQuantity(conflict.entityId, remoteQuantity, now)
                    deliveryUpdated = true
                    finalValue = remoteQuantity.toString()
                }
            }
        }
    }

    if (finalValue == null) {
        finalValue = conflict.localValue ?: conflict.remoteValue ?: "(null)"
    }

    val strategy = mergeStrategyForField(conflict.fieldName)
    val resolutionDetail = "$action|final=${finalValue ?: "(null)"}|strategy=$strategy"

    if (deliveryUpdated) {
        appendMutation(
            repository = repository,
            entityType = "delivery",
            entityId = conflict.entityId,
            operationType = "MERGE_RESOLVE",
            changedFieldsJson = changedFieldsJson(
                mapOf(
                    "field" to conflict.fieldName,
                    "local" to (conflict.localValue ?: "(null)"),
                    "remote" to (conflict.remoteValue ?: "(null)"),
                    "strategy" to strategy,
                    "resolution" to action,
                    "final" to (finalValue ?: "(null)"),
                ),
            ),
        )
    }

    repository.resolveConflict(
        conflictId = conflict.conflictId,
        resolvedAt = now,
        resolution = resolutionDetail,
    )
    appendMutation(
        repository = repository,
        entityType = "conflict",
        entityId = conflict.conflictId,
        operationType = "RESOLVE",
        changedFieldsJson = changedFieldsJson(
            mapOf(
                "field" to conflict.fieldName,
                "strategy" to strategy,
                "resolution" to action,
                "final" to (finalValue ?: "(null)"),
                "status" to "RESOLVED",
            ),
        ),
    )
}
