package me.zayedbinhasan.android_app.ui.models

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
