package me.zayedbinhasan.android_app.ui.logic.m2_crdt

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
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
    appendMutation(repository, entityType = "delivery", entityId = deliveryId, operationType = "UPSERT")
}

internal fun deleteDelivery(repository: LocalRepository, taskId: String) {
    repository.deleteDeliveryById(taskId)
    appendMutation(repository, entityType = "delivery", entityId = taskId, operationType = "DELETE")
}
