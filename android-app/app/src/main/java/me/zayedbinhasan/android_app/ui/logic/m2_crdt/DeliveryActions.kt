package me.zayedbinhasan.android_app.ui.logic.m2_crdt

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import me.zayedbinhasan.android_app.ui.logic.core.changedFieldsJson
import java.util.UUID

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
    appendMutation(
        repository = repository,
        entityType = "delivery",
        entityId = deliveryId,
        operationType = "UPSERT",
        changedFieldsJson = changedFieldsJson(
            mapOf(
                "supply_id" to "medical-kit-a",
                "quantity" to "25",
                "origin_id" to "warehouse-01",
                "destination_id" to "camp-03",
                "priority" to "P1_HIGH",
                "assigned_driver_id" to "driver-07",
                "status" to "PENDING",
            ),
        ),
    )
}

internal fun deleteDelivery(repository: LocalRepository, taskId: String) {
    repository.deleteDeliveryById(taskId)
    appendMutation(
        repository = repository,
        entityType = "delivery",
        entityId = taskId,
        operationType = "DELETE",
        changedFieldsJson = changedFieldsJson(
            mapOf(
                "status" to "DELETED",
            ),
        ),
    )
}
