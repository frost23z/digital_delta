package me.zayedbinhasan.android_app.ui.screens

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import me.zayedbinhasan.android_app.ui.core.DEFAULT_LOCAL_NODE_ID
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_GRPC_HOST
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_GRPC_PORT
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_HTTP_BASE_URL
import me.zayedbinhasan.android_app.ui.core.DEFAULT_SYNC_PEER_ID
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.applyIncomingMutationBatch
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.applyIncomingMutations
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.simulateSyncWithPeer
import me.zayedbinhasan.android_app.ui.logic.m3_mesh.PeerTransferStatus
import me.zayedbinhasan.android_app.ui.logic.m3_mesh.performPeerDeltaSyncLan
import me.zayedbinhasan.android_app.ui.logic.m3_mesh.performServerDeltaSync
import me.zayedbinhasan.android_app.ui.logic.m3_mesh.performServerDeltaSyncHttpFallback
import me.zayedbinhasan.android_app.ui.logic.m3_mesh.receivePeerDeltaSyncOnce
import me.zayedbinhasan.android_app.ui.models.MutationUi
import me.zayedbinhasan.android_app.ui.models.SyncCheckpointUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SYNC_UI_LOG_TAG = "DigitalDeltaSyncUI"
private const val DEFAULT_WIFI_HOST = "192.168.68.131"
private const val DEFAULT_WIFI_SYNC_HTTP_BASE_URL = "http://$DEFAULT_WIFI_HOST:8081"

@Composable
internal fun SyncStatusScreen(repository: LocalRepository) {
    val peerId = DEFAULT_SYNC_PEER_ID
    val localNodeId = DEFAULT_LOCAL_NODE_ID
    val coroutineScope = rememberCoroutineScope()
    val runningOnEmulator = remember { isLikelyEmulator() }

    var syncInProgress by rememberSaveable { mutableStateOf(false) }
    var syncMessage by rememberSaveable { mutableStateOf("Idle") }
    var lastServerSyncAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var peerSyncMessage by rememberSaveable { mutableStateOf("Idle") }
    var lastPeerSyncAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var peerHostInput by rememberSaveable { mutableStateOf("") }
    var peerPortInput by rememberSaveable { mutableStateOf("9099") }
    var listenPortInput by rememberSaveable { mutableStateOf("9099") }
    var peerTransferStatuses by remember { mutableStateOf(emptyList<PeerTransferStatus>()) }
    var useHttpDevFallback by rememberSaveable { mutableStateOf(false) }
    var syncGrpcHostInput by rememberSaveable {
        mutableStateOf(if (runningOnEmulator) DEFAULT_SYNC_GRPC_HOST else DEFAULT_WIFI_HOST)
    }
    var syncGrpcPortInput by rememberSaveable { mutableStateOf(DEFAULT_SYNC_GRPC_PORT.toString()) }
    var syncServerBaseUrlInput by rememberSaveable {
        mutableStateOf(if (runningOnEmulator) DEFAULT_SYNC_HTTP_BASE_URL else DEFAULT_WIFI_SYNC_HTTP_BASE_URL)
    }

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
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sync Status", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pending Changes: ${pendingMutations.size}")
                Text("Latest Mutation: ${latestMutationTimestamp ?: "None"}")
                Text("Unseen For $peerId: ${unseenForPeer.size}")
                Text("Connection: Ready when peer/server is reachable")
                OutlinedTextField(
                    value = syncGrpcHostInput,
                    onValueChange = { syncGrpcHostInput = it },
                    label = { Text("Server gRPC Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = syncGrpcPortInput,
                    onValueChange = { syncGrpcPortInput = it },
                    label = { Text("Server gRPC Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = syncServerBaseUrlInput,
                    onValueChange = { syncServerBaseUrlInput = it },
                    label = { Text("HTTP Fallback Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    if (runningOnEmulator) {
                        "Detected emulator: use 10.0.2.2 to reach host machine."
                    } else {
                        "Detected physical device: do not use 10.0.2.2. Use host LAN IP or adb reverse with 127.0.0.1."
                    },
                )
                Text(
                    "Transport Compliance: ${if (useHttpDevFallback) "NON_COMPLIANT (HTTP JSON DEV FALLBACK)" else "COMPLIANT (gRPC + Protobuf)"}",
                    fontWeight = FontWeight.Bold,
                )
                if (useHttpDevFallback) {
                    Text("Warning: DEV fallback is active and does not satisfy C1 requirements.")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Use DEV HTTP fallback")
                    Switch(
                        checked = useHttpDevFallback,
                        onCheckedChange = { useHttpDevFallback = it },
                        enabled = !syncInProgress,
                    )
                }
                Text("Sync State: $syncMessage")
                Text("Last Server Sync: ${lastServerSyncAt ?: "Never"}")
                Button(
                    onClick = {
                        if (syncInProgress) {
                            return@Button
                        }

                        val grpcPort = syncGrpcPortInput.toIntOrNull()
                        val grpcHost = syncGrpcHostInput.trim()
                        val httpBaseUrl = syncServerBaseUrlInput.trim()

                        if (!useHttpDevFallback && (grpcHost.isBlank() || grpcPort == null)) {
                            syncMessage = "SYNC_CONFIG_INVALID_GRPC"
                            Log.w(
                                SYNC_UI_LOG_TAG,
                                "Sync config invalid (gRPC): host='$grpcHost' port='${syncGrpcPortInput.trim()}'",
                            )
                            return@Button
                        }
                        if (!runningOnEmulator && !useHttpDevFallback && grpcHost == "10.0.2.2") {
                            syncMessage = "SYNC_CONFIG_INVALID_GRPC_HOST_PHYSICAL_DEVICE"
                            Log.w(
                                SYNC_UI_LOG_TAG,
                                "gRPC host 10.0.2.2 is emulator-only. Physical device must use host LAN IP or adb reverse + 127.0.0.1",
                            )
                            return@Button
                        }
                        if (useHttpDevFallback && httpBaseUrl.isBlank()) {
                            syncMessage = "SYNC_CONFIG_INVALID_HTTP"
                            Log.w(
                                SYNC_UI_LOG_TAG,
                                "Sync config invalid (HTTP fallback): baseUrl is blank",
                            )
                            return@Button
                        }
                        if (!runningOnEmulator && useHttpDevFallback && httpBaseUrl.contains("10.0.2.2")) {
                            syncMessage = "SYNC_CONFIG_INVALID_HTTP_HOST_PHYSICAL_DEVICE"
                            Log.w(
                                SYNC_UI_LOG_TAG,
                                "HTTP baseUrl 10.0.2.2 is emulator-only. Physical device must use host LAN IP or adb reverse + 127.0.0.1",
                            )
                            return@Button
                        }

                        syncInProgress = true
                        syncMessage = "SYNCING"

                        coroutineScope.launch {
                            val serverOutcome = withContext(Dispatchers.IO) {
                                if (useHttpDevFallback) {
                                    performServerDeltaSyncHttpFallback(
                                        baseUrl = httpBaseUrl.trimEnd('/'),
                                        nodeId = localNodeId,
                                        checkpointCounter = activePeerCheckpoint?.lastSeenCounter ?: 0L,
                                        outgoingMutations = pendingMutationsRaw,
                                    )
                                } else {
                                    performServerDeltaSync(
                                        host = grpcHost,
                                        port = grpcPort ?: DEFAULT_SYNC_GRPC_PORT,
                                        nodeId = localNodeId,
                                        checkpointCounter = activePeerCheckpoint?.lastSeenCounter ?: 0L,
                                        outgoingMutations = pendingMutationsRaw,
                                    )
                                }
                            }

                            val serverResult = serverOutcome.result

                            if (serverResult == null) {
                                syncMessage = if (useHttpDevFallback) {
                                    "SYNC_ERROR_HTTP_DEV_FALLBACK: ${serverOutcome.errorDetail ?: "unknown"}"
                                } else {
                                    "SYNC_ERROR_GRPC: ${serverOutcome.errorDetail ?: "unknown"}"
                                }
                                Log.e(
                                    SYNC_UI_LOG_TAG,
                                    "Server sync failed with state='$syncMessage' (useHttpFallback=$useHttpDevFallback, grpcHost='${syncGrpcHostInput.trim()}', grpcPort='${syncGrpcPortInput.trim()}', httpBaseUrl='${syncServerBaseUrlInput.trim()}')",
                                )
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

                            lastServerSyncAt = now
                            syncMessage = serverResult.message.ifEmpty {
                                if (useHttpDevFallback) "SYNC_OK_HTTP_DEV_FALLBACK" else "SYNC_OK_GRPC"
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

        if (checkpoints.isEmpty()) {
            Text("No checkpoints recorded")
        } else {
            checkpoints.forEach { checkpoint ->
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

        if (pendingMutations.isEmpty()) {
            Text("No pending mutations")
        } else {
            pendingMutations.forEach { mutation ->
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

private fun isLikelyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    return fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        product.contains("sdk") ||
        product.contains("emulator")
}
