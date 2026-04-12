package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.DEFAULT_LOCAL_NODE_ID
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_HTTP_BASE_URL
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_PEER_ID
import me.zayedbinhasan.android_app.ui.logic.PeerTransferStatus
import me.zayedbinhasan.android_app.ui.logic.appendMutation
import me.zayedbinhasan.android_app.ui.logic.applyIncomingMutationBatch
import me.zayedbinhasan.android_app.ui.logic.applyIncomingMutations
import me.zayedbinhasan.android_app.ui.logic.performPeerDeltaSyncLan
import me.zayedbinhasan.android_app.ui.logic.performServerDeltaSync
import me.zayedbinhasan.android_app.ui.logic.receivePeerDeltaSyncOnce
import me.zayedbinhasan.android_app.ui.logic.simulateSyncWithPeer
import me.zayedbinhasan.android_app.ui.models.MutationUi
import me.zayedbinhasan.android_app.ui.models.SyncCheckpointUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SyncStatusScreen(repository: LocalRepository) {
    val peerId = DEFAULT_SYNC_PEER_ID
    val syncServerBaseUrl = DEFAULT_SYNC_HTTP_BASE_URL
    val localNodeId = DEFAULT_LOCAL_NODE_ID
    val coroutineScope = rememberCoroutineScope()

    var syncInProgress by rememberSaveable { mutableStateOf(false) }
    var syncMessage by rememberSaveable { mutableStateOf("Idle") }
    var lastServerSyncAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var peerSyncMessage by rememberSaveable { mutableStateOf("Idle") }
    var lastPeerSyncAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var peerHostInput by rememberSaveable { mutableStateOf("") }
    var peerPortInput by rememberSaveable { mutableStateOf("9099") }
    var listenPortInput by rememberSaveable { mutableStateOf("9099") }
    var peerTransferStatuses by remember { mutableStateOf(emptyList<PeerTransferStatus>()) }

    val pendingMutationsRaw by remember(repository) {
        repository.observePendingMutations()
    }.collectAsState(initial = emptyList())

    val latestMutationTimestamp by remember(repository) {
        repository.observeLatestMutationTimestamp()
    }.collectAsState(initial = null)

    val checkpointsRaw by remember(repository) {
        repository.observeSyncCheckpoints()
    }.collectAsState(initial = emptyList())

    val unseenForPeerRaw by remember(repository, peerId) {
        repository.observeUnseenMutationsForPeer(peerId)
    }.collectAsState(initial = emptyList())

    val pendingMutations = pendingMutationsRaw.map { row ->
        MutationUi(
            mutationId = row.mutation_id,
            entityType = row.entity_type,
            entityId = row.entity_id,
            operationType = row.operation_type,
            deviceId = row.device_id,
            mutationTimestamp = row.mutation_timestamp,
        )
    }

    val checkpoints = checkpointsRaw.map { row ->
        SyncCheckpointUi(
            peerId = row.peer_id,
            lastSeenCounter = row.last_seen_counter,
            lastSyncTimestamp = row.last_sync_timestamp,
            updatedAt = row.updated_at,
        )
    }

    val unseenForPeer = unseenForPeerRaw.map { row ->
        MutationUi(
            mutationId = row.mutation_id,
            entityType = row.entity_type,
            entityId = row.entity_id,
            operationType = row.operation_type,
            deviceId = row.device_id,
            mutationTimestamp = row.mutation_timestamp,
        )
    }

    val activePeerCheckpoint = checkpoints.firstOrNull { it.peerId == peerId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sync Status", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pending Changes: ${pendingMutations.size}")
                Text("Latest Mutation: ${latestMutationTimestamp ?: "None"}")
                Text("Unseen For $peerId: ${unseenForPeer.size}")
                Text("Connection: Ready when peer/server is reachable")
                Text("Sync State: $syncMessage")
                Text("Last Server Sync: ${lastServerSyncAt ?: "Never"}")
                Button(
                    onClick = {
                        if (syncInProgress) {
                            return@Button
                        }

                        syncInProgress = true
                        syncMessage = "SYNCING"

                        coroutineScope.launch {
                            val serverResult = withContext(Dispatchers.IO) {
                                performServerDeltaSync(
                                    baseUrl = syncServerBaseUrl,
                                    nodeId = localNodeId,
                                    checkpointCounter = activePeerCheckpoint?.lastSeenCounter ?: 0L,
                                    outgoingMutations = pendingMutationsRaw,
                                )
                            }

                            if (serverResult == null) {
                                syncMessage = "SYNC_ERROR"
                                syncInProgress = false
                                return@launch
                            }

                            if (serverResult.incomingMutations.isNotEmpty()) {
                                applyIncomingMutations(repository, serverResult.incomingMutations)
                            }

                            if (serverResult.syncedMutationIds.isNotEmpty()) {
                                repository.markAllMutationsSynced()
                            }

                            val checkpointCounter = serverResult.updatedCheckpoint.values.maxOrNull() ?: 0L
                            val now = System.currentTimeMillis()
                            repository.upsertSyncCheckpoint(
                                peerId = peerId,
                                lastSeenCounter = checkpointCounter,
                                lastSyncTimestamp = now,
                                updatedAt = now,
                            )
                            appendMutation(
                                repository,
                                entityType = "sync_checkpoint",
                                entityId = peerId,
                                operationType = "UPSERT"
                            )

                            lastServerSyncAt = now
                            syncMessage = serverResult.message.ifEmpty {
                                "SYNC_OK"
                            }
                            syncInProgress = false
                        }
                    },
                    enabled = !syncInProgress,
                ) {
                    Text(if (syncInProgress) "Syncing..." else "Sync Now (Server)")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Peer Sync State: $peerSyncMessage")
                Text("Last Peer Sync: ${lastPeerSyncAt ?: "Never"}")
                Text("Transport Mode: Direct LAN TCP (device-to-device, no central server)")
                OutlinedTextField(
                    value = peerHostInput,
                    onValueChange = { peerHostInput = it },
                    label = { Text("Peer Host (LAN IP)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = peerPortInput,
                    onValueChange = { peerPortInput = it },
                    label = { Text("Peer Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = listenPortInput,
                    onValueChange = { listenPortInput = it },
                    label = { Text("Listen Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (syncInProgress) {
                            return@Button
                        }

                        val peerPort = peerPortInput.toIntOrNull()
                        if (peerHostInput.isBlank() || peerPort == null) {
                            peerSyncMessage = "PEER_SYNC_CONFIG_INVALID"
                            return@Button
                        }

                        syncInProgress = true
                        peerSyncMessage = "PEER_SYNCING"

                        coroutineScope.launch {
                            val peerResult = withContext(Dispatchers.Default) {
                                performPeerDeltaSyncLan(
                                    host = peerHostInput,
                                    port = peerPort,
                                    nodeId = localNodeId,
                                    checkpointCounter = activePeerCheckpoint?.lastSeenCounter ?: 0L,
                                    outgoingMutations = pendingMutationsRaw,
                                )
                            }

                            peerTransferStatuses = peerResult.transferStatuses

                            if (peerResult.incomingMutations.isNotEmpty()) {
                                applyIncomingMutations(repository, peerResult.incomingMutations)
                            }

                            if (peerResult.syncedMutationIds.isNotEmpty()) {
                                repository.markAllMutationsSynced()
                            }

                            val now = System.currentTimeMillis()
                            if (peerResult.transferStatuses.any { it.success }) {
                                val checkpointCounter = activePeerCheckpoint?.lastSeenCounter ?: 0L
                                repository.upsertSyncCheckpoint(
                                    peerId = peerId,
                                    lastSeenCounter = checkpointCounter + peerResult.syncedMutationIds.size,
                                    lastSyncTimestamp = now,
                                    updatedAt = now,
                                )
                                appendMutation(
                                    repository,
                                    entityType = "sync_checkpoint",
                                    entityId = peerId,
                                    operationType = "UPSERT"
                                )
                                peerSyncMessage = "PEER_SYNC_OK"
                            } else {
                                peerSyncMessage = "PEER_SYNC_FAILED"
                            }

                            lastPeerSyncAt = now
                            syncInProgress = false
                        }
                    },
                    enabled = !syncInProgress,
                ) {
                    Text(if (syncInProgress) "Syncing..." else "Peer Sync Now (LAN)")
                }

                Button(
                    onClick = {
                        if (syncInProgress) {
                            return@Button
                        }
                        val listenPort = listenPortInput.toIntOrNull()
                        if (listenPort == null) {
                            peerSyncMessage = "PEER_LISTENER_PORT_INVALID"
                            return@Button
                        }

                        syncInProgress = true
                        peerSyncMessage = "PEER_LISTENING"

                        coroutineScope.launch {
                            val receiveStatus = withContext(Dispatchers.IO) {
                                receivePeerDeltaSyncOnce(
                                    repository = repository,
                                    nodeId = localNodeId,
                                    listenPort = listenPort,
                                )
                            }

                            val now = System.currentTimeMillis()
                            peerTransferStatuses = listOf(receiveStatus) + peerTransferStatuses.take(7)
                            lastPeerSyncAt = now
                            peerSyncMessage = if (receiveStatus.success) {
                                "PEER_RECEIVE_OK"
                            } else {
                                "PEER_RECEIVE_FAILED"
                            }
                            syncInProgress = false
                        }
                    },
                    enabled = !syncInProgress,
                ) {
                    Text(if (syncInProgress) "Listening..." else "Receive Peer Sync Once")
                }

                if (peerTransferStatuses.isNotEmpty()) {
                    Text("Peer Transfer Status", fontWeight = FontWeight.Bold)
                    peerTransferStatuses.forEach { transfer ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            "${transfer.peerId}: ${if (transfer.success) "SUCCESS" else "FAILED"}",
                                        )
                                    },
                                )
                                Text("message_id: ${transfer.messageId}")
                                Text("attempt_count: ${transfer.attemptCount}")
                                Text("ttl: ${transfer.ttl}")
                                Text("seen_nodes: ${transfer.seenNodes.joinToString(",")}")
                                Text("transfer: ${transfer.detail}")
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        simulateSyncWithPeer(
                            repository = repository,
                            appliedMutationCount = unseenForPeer.size,
                        )
                    },
                ) {
                    Text("Simulate Sync With Peer")
                }
                Button(onClick = { applyIncomingMutationBatch(repository) }) {
                    Text("Apply Incoming Mutation Batch")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Checkpoint (${peerId})", fontWeight = FontWeight.Bold)
                if (activePeerCheckpoint == null) {
                    Text("No checkpoint saved yet")
                } else {
                    Text("Last Seen Counter: ${activePeerCheckpoint.lastSeenCounter}")
                    Text("Last Sync Timestamp: ${activePeerCheckpoint.lastSyncTimestamp}")
                    Text("Updated At: ${activePeerCheckpoint.updatedAt}")
                }
            }
        }

        HorizontalDivider()

        Text("Known Peer Checkpoints", fontWeight = FontWeight.Bold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(checkpoints, key = { it.peerId }) { checkpoint ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(checkpoint.peerId, fontWeight = FontWeight.Bold)
                        Text("Counter: ${checkpoint.lastSeenCounter}")
                        Text("Last Sync: ${checkpoint.lastSyncTimestamp}")
                        Text("Updated: ${checkpoint.updatedAt}")
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Pending Mutation Queue", fontWeight = FontWeight.Bold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pendingMutations, key = { it.mutationId }) { mutation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${mutation.operationType} ${mutation.entityType}", fontWeight = FontWeight.Bold)
                        Text("Entity: ${mutation.entityId}")
                        Text("Device: ${mutation.deviceId}")
                        Text("Queued at: ${mutation.mutationTimestamp}")
                    }
                }
            }
        }
    }
}
