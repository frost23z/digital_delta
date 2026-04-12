package me.zayedbinhasan.android_app.data.local.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import me.zayedbinhasan.data.Conflicts
import me.zayedbinhasan.data.Deliveries
import me.zayedbinhasan.data.LocalQueries
import me.zayedbinhasan.data.Mutation_logs
import me.zayedbinhasan.data.Receipts
import me.zayedbinhasan.data.Routes
import me.zayedbinhasan.data.Sync_checkpoints
import me.zayedbinhasan.data.Users

class LocalRepository(
    private val queries: LocalQueries,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeOpenConflictCount(): Flow<Long> =
        queries.selectOpenConflictCount().asFlow().mapToOne(ioDispatcher)

    fun observeUsers(): Flow<List<Users>> =
        queries.selectAllUsers().asFlow().mapToList(ioDispatcher)

    fun observeDeliveries(): Flow<List<Deliveries>> =
        queries.selectAllDeliveries().asFlow().mapToList(ioDispatcher)

    fun observeRoutes(): Flow<List<Routes>> =
        queries.selectAllRoutes().asFlow().mapToList(ioDispatcher)

    fun observeReceipts(): Flow<List<Receipts>> =
        queries.selectAllReceipts().asFlow().mapToList(ioDispatcher)

    fun observePendingMutations(): Flow<List<Mutation_logs>> =
        queries.selectPendingMutations().asFlow().mapToList(ioDispatcher)

    fun observeLatestMutationTimestamp(): Flow<Long?> =
        queries.selectLatestMutationTimestamp().asFlow().mapToOneOrNull(ioDispatcher)

    fun observeSyncCheckpoints(): Flow<List<Sync_checkpoints>> =
        queries.selectAllSyncCheckpoints().asFlow().mapToList(ioDispatcher)

    fun observeUnseenMutationsForPeer(peerId: String): Flow<List<Mutation_logs>> =
        queries.selectUnseenMutationsForPeer(peerId).asFlow().mapToList(ioDispatcher)

    fun observeOpenConflicts(): Flow<List<Conflicts>> =
        queries.selectOpenConflicts().asFlow().mapToList(ioDispatcher)

    fun firstDeliveryOrNull(): Deliveries? =
        queries.selectAllDeliveries().executeAsList().firstOrNull()

    fun deliveryById(taskId: String): Deliveries? =
        queries.selectDeliveryById(taskId).executeAsOneOrNull()

    fun syncCheckpointByPeer(peerId: String): Sync_checkpoints? =
        queries.selectSyncCheckpointByPeer(peerId).executeAsOneOrNull()

    fun localDeviceMutationCount(deviceId: String): Long =
        queries.selectLocalDeviceMutationCount(deviceId).executeAsOne()

    fun latestMutationTimestampNow(): Long? =
        queries.selectLatestMutationTimestamp().executeAsOneOrNull()

    fun upsertUser(
        userId: String,
        displayName: String,
        role: String,
        publicKey: String?,
        active: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ) {
        queries.upsertUser(
            user_id = userId,
            display_name = displayName,
            role = role,
            public_key = publicKey,
            active = active,
            created_at = createdAt,
            updated_at = updatedAt,
        )
    }

    fun upsertDelivery(
        taskId: String,
        supplyId: String,
        quantity: Long,
        originId: String,
        destinationId: String,
        priority: String,
        deadlineTimestamp: Long,
        assignedDriverId: String?,
        status: String,
        updatedAt: Long,
    ) {
        queries.upsertDelivery(
            task_id = taskId,
            supply_id = supplyId,
            quantity = quantity,
            origin_id = originId,
            destination_id = destinationId,
            priority = priority,
            deadline_timestamp = deadlineTimestamp,
            assigned_driver_id = assignedDriverId,
            status = status,
            updated_at = updatedAt,
        )
    }

    fun upsertRoute(
        routeId: String,
        edgeIdsJson: String,
        totalDurationMins: Long,
        vehicle: String,
        etaTimestamp: Long,
        reasonCode: String,
        updatedAt: Long,
    ) {
        queries.upsertRoute(
            route_id = routeId,
            edge_ids_json = edgeIdsJson,
            total_duration_mins = totalDurationMins,
            vehicle = vehicle,
            eta_timestamp = etaTimestamp,
            reason_code = reasonCode,
            updated_at = updatedAt,
        )
    }

    fun upsertReceipt(
        receiptId: String,
        deliveryId: String,
        senderUserId: String,
        recipientUserId: String,
        payloadHash: String,
        nonce: Long,
        senderSignature: String,
        recipientSignature: String,
        verified: Boolean,
        verifiedAt: Long?,
    ) {
        queries.upsertReceipt(
            receipt_id = receiptId,
            delivery_id = deliveryId,
            sender_user_id = senderUserId,
            recipient_user_id = recipientUserId,
            payload_hash = payloadHash,
            nonce = nonce,
            sender_signature = senderSignature,
            recipient_signature = recipientSignature,
            verified = verified,
            verified_at = verifiedAt,
        )
    }

    fun deleteUserById(userId: String) {
        queries.deleteUserById(userId)
    }

    fun deleteDeliveryById(taskId: String) {
        queries.deleteDeliveryById(taskId)
    }

    fun deleteRouteById(routeId: String) {
        queries.deleteRouteById(routeId)
    }

    fun deleteReceiptById(receiptId: String) {
        queries.deleteReceiptById(receiptId)
    }

    fun insertMutation(
        mutationId: String,
        entityType: String,
        entityId: String,
        operationType: String,
        changedFieldsJson: String,
        vectorClockJson: String,
        actorId: String,
        deviceId: String,
        mutationTimestamp: Long,
        synced: Boolean,
    ) {
        queries.insertMutation(
            mutation_id = mutationId,
            entity_type = entityType,
            entity_id = entityId,
            operation_type = operationType,
            changed_fields_json = changedFieldsJson,
            vector_clock_json = vectorClockJson,
            actor_id = actorId,
            device_id = deviceId,
            mutation_timestamp = mutationTimestamp,
            synced = synced,
        )
    }

    fun markAllMutationsSynced() {
        queries.markAllMutationsSynced()
    }

    fun upsertSyncCheckpoint(peerId: String, lastSeenCounter: Long, lastSyncTimestamp: Long, updatedAt: Long) {
        queries.upsertSyncCheckpoint(
            peer_id = peerId,
            last_seen_counter = lastSeenCounter,
            last_sync_timestamp = lastSyncTimestamp,
            updated_at = updatedAt,
        )
    }

    fun updateDeliveryStatus(taskId: String, status: String, updatedAt: Long) {
        queries.updateDeliveryStatus(
            status = status,
            updated_at = updatedAt,
            task_id = taskId,
        )
    }

    fun updateDeliveryQuantity(taskId: String, quantity: Long, updatedAt: Long) {
        queries.updateDeliveryQuantity(
            quantity = quantity,
            updated_at = updatedAt,
            task_id = taskId,
        )
    }

    fun updateDeliveryAssignee(taskId: String, assignedDriverId: String?, updatedAt: Long) {
        queries.updateDeliveryAssignee(
            assigned_driver_id = assignedDriverId,
            updated_at = updatedAt,
            task_id = taskId,
        )
    }

    fun insertConflict(
        conflictId: String,
        entityType: String,
        entityId: String,
        fieldName: String,
        localValue: String?,
        remoteValue: String?,
        mergeStrategy: String,
        manualRequired: Boolean,
        status: String,
        createdAt: Long,
        resolvedAt: Long?,
        resolution: String?,
    ) {
        queries.insertConflict(
            conflict_id = conflictId,
            entity_type = entityType,
            entity_id = entityId,
            field_name = fieldName,
            local_value = localValue,
            remote_value = remoteValue,
            merge_strategy = mergeStrategy,
            manual_required = manualRequired,
            status = status,
            created_at = createdAt,
            resolved_at = resolvedAt,
            resolution = resolution,
        )
    }

    fun resolveConflict(conflictId: String, resolvedAt: Long, resolution: String) {
        queries.resolveConflict(
            resolved_at = resolvedAt,
            resolution = resolution,
            conflict_id = conflictId,
        )
    }
}
