package me.zayedbinhasan.android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zayedbinhasan.android_app.data.local.db.LocalDatabaseFactory
import me.zayedbinhasan.android_app.ui.theme.AndroidappTheme
import me.zayedbinhasan.data.Database
import me.zayedbinhasan.digitaldelta.proto.DeltaSyncRequest
import me.zayedbinhasan.digitaldelta.proto.Mutation
import me.zayedbinhasan.digitaldelta.proto.SyncCheckpoint
import me.zayedbinhasan.digitaldelta.proto.SyncServiceGrpc
import me.zayedbinhasan.digitaldelta.proto.VectorClock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DEFAULT_GRPC_HOST = "10.0.2.2"
private const val DEFAULT_GRPC_PORT = "50051"
private const val DEFAULT_NODE_ID = "android-node-1"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class SyncUiState(
    val isSyncing: Boolean = false,
    val pendingMutations: Int = 0,
    val lastSyncEpochMillis: Long? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

data class DashboardDbSnapshot(
    val userRows: List<String> = emptyList(),
    val deliveryRows: List<String> = emptyList(),
    val pendingMutationRows: List<String> = emptyList(),
)

data class SyncResult(
    val ok: Boolean,
    val syncedCount: Int,
    val incomingCount: Int,
    val message: String,
)

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    var syncState by remember { mutableStateOf(SyncUiState()) }
    var snapshot by remember { mutableStateOf(DashboardDbSnapshot()) }
    var syncedSnapshot by remember { mutableStateOf(DashboardDbSnapshot()) }
    var isLoadingDb by remember { mutableStateOf(true) }
    var grpcHost by remember { mutableStateOf(DEFAULT_GRPC_HOST) }
    var grpcPortText by remember { mutableStateOf(DEFAULT_GRPC_PORT) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember { LocalDatabaseFactory.create(context) }

    suspend fun refreshSnapshot(): DashboardDbSnapshot {
        isLoadingDb = true
        val freshSnapshot = withContext(Dispatchers.IO) {
            readDashboardSnapshot(database)
        }
        snapshot = freshSnapshot
        syncState = syncState.copy(pendingMutations = freshSnapshot.pendingMutationRows.size)
        isLoadingDb = false
        return freshSnapshot
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            seedDemoDataIfNeeded(database)
        }
        refreshSnapshot()
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Digital Delta Dashboard",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "gRPC target: $grpcHost:$grpcPortText",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Emulator uses 10.0.2.2. On physical phone, use your laptop LAN IP.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isLoadingDb) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                seedDemoData(database)
                            }
                            refreshSnapshot()
                            syncState = syncState.copy(
                                statusMessage = "Inserted demo rows into SQLite.",
                                isError = false,
                            )
                        }
                    },
                    enabled = !isSyncingOrLoading(syncState.isSyncing, isLoadingDb),
                ) {
                    Text("Seed SQLite Data")
                }

                OutlinedTextField(
                    value = grpcHost,
                    onValueChange = { grpcHost = it.trim() },
                    label = { Text("gRPC Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = grpcPortText,
                    onValueChange = { grpcPortText = it.trim() },
                    label = { Text("gRPC Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Pending mutations: ${syncState.pendingMutations}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Users from SQLite after sync: ${syncedSnapshot.userRows.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Deliveries from SQLite after sync: ${syncedSnapshot.deliveryRows.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Button(
                    onClick = {
                        scope.launch {
                            syncedSnapshot = DashboardDbSnapshot()
                            syncState = syncState.copy(
                                isSyncing = true,
                                statusMessage = null,
                                isError = false,
                            )

                            val result = withContext(Dispatchers.IO) {
                                syncPendingMutationsToServer(
                                    database = database,
                                    host = grpcHost,
                                    portText = grpcPortText,
                                )
                            }
                            val refreshed = refreshSnapshot()

                            syncState = if (result.ok) {
                                syncedSnapshot = refreshed
                                syncState.copy(
                                    isSyncing = false,
                                    lastSyncEpochMillis = System.currentTimeMillis(),
                                    statusMessage = result.message,
                                    isError = false,
                                )
                            } else {
                                syncState.copy(
                                    isSyncing = false,
                                    statusMessage = result.message,
                                    isError = true,
                                )
                            }
                        }
                    },
                    enabled = !isSyncingOrLoading(syncState.isSyncing, isLoadingDb),
                ) {
                    Text(if (syncState.isSyncing) "Syncing..." else "Sync Now")
                }

                if (syncState.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }

                DataPreviewSection(
                    title = "SQLite Users",
                    rows = syncedSnapshot.userRows,
                )
                DataPreviewSection(
                    title = "SQLite Deliveries",
                    rows = syncedSnapshot.deliveryRows,
                )
                DataPreviewSection(
                    title = "Pending SQLite Mutations",
                    rows = syncedSnapshot.pendingMutationRows,
                )

                Text(
                    text = "Last sync: ${formatLastSync(syncState.lastSyncEpochMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (syncState.statusMessage != null) {
                    Text(
                        text = syncState.statusMessage.orEmpty(),
                        color = if (syncState.isError) Color(0xFFB3261E) else Color(0xFF1B5E20),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun DataPreviewSection(title: String, rows: List<String>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
    )

    if (rows.isEmpty()) {
        Text(
            text = "No rows",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    rows.take(3).forEach { rowText ->
        Text(
            text = rowText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun isSyncingOrLoading(isSyncing: Boolean, isLoadingDb: Boolean): Boolean {
    return isSyncing || isLoadingDb
}

private fun seedDemoDataIfNeeded(database: Database) {
    val hasUsers = database.localQueries.selectAllUsers().executeAsList().isNotEmpty()
    if (!hasUsers) {
        seedDemoData(database)
    }
}

private fun seedDemoData(database: Database) {
    val now = System.currentTimeMillis()
    val mutationId = "mut-$now"

    database.transaction {
        database.localQueries.upsertUser(
            "user-1",
            "Nadia Rahman",
            "SUPPLY_MANAGER",
            "demo-public-key-1",
            true,
            now,
            now,
        )
        database.localQueries.upsertUser(
            "user-2",
            "Reza Karim",
            "FIELD_VOLUNTEER",
            "demo-public-key-2",
            true,
            now,
            now,
        )

        database.localQueries.upsertDelivery(
            "task-1",
            "supply-med-1",
            120,
            "origin-A",
            "camp-3",
            "P0_CRITICAL",
            now + 3_600_000,
            "driver-1",
            "IN_TRANSIT",
            now,
        )
        database.localQueries.upsertDelivery(
            "task-2",
            "supply-water-1",
            80,
            "origin-B",
            "camp-5",
            "P1_HIGH",
            now + 7_200_000,
            "driver-2",
            "PENDING",
            now,
        )

        database.localQueries.insertMutation(
            mutationId,
            "DeliveryTask",
            "task-1",
            "UPDATE",
            "{\"status\":\"IN_TRANSIT\"}",
            "user-1",
            "android-node-1",
            now,
            false,
        )
    }
}

private fun readDashboardSnapshot(database: Database): DashboardDbSnapshot {
    return DashboardDbSnapshot(
        userRows = database.localQueries
            .selectAllUsers()
            .executeAsList()
            .map { it.toString() },
        deliveryRows = database.localQueries
            .selectAllDeliveries()
            .executeAsList()
            .map { it.toString() },
        pendingMutationRows = database.localQueries
            .selectPendingMutations()
            .executeAsList()
            .map { it.toString() },
    )
}

private fun syncPendingMutationsToServer(database: Database, host: String, portText: String): SyncResult {
    val pending = database.localQueries.selectPendingMutations().executeAsList()
    if (pending.isEmpty()) {
        return SyncResult(
            ok = true,
            syncedCount = 0,
            incomingCount = 0,
            message = "No pending SQLite mutations to sync.",
        )
    }

    val port = portText.toIntOrNull()
    if (host.isBlank() || port == null || port <= 0) {
        return SyncResult(
            ok = false,
            syncedCount = 0,
            incomingCount = 0,
            message = "Invalid gRPC host/port. Example: 10.0.2.2 and 50051",
        )
    }

    val channel = OkHttpChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    return try {
        val outgoingMutations = pending.map { row ->
            val changedFieldMap = parseChangedFields(row.changed_fields_json)
            Mutation.newBuilder()
                .setMutationId(row.mutation_id)
                .setEntityType(row.entity_type)
                .setEntityId(row.entity_id)
                .putAllChangedFields(changedFieldMap)
                .setVectorClock(
                    VectorClock.newBuilder().putCounters(
                        row.device_id.ifBlank { DEFAULT_NODE_ID },
                        row.mutation_timestamp,
                    )
                )
                .setActorId(row.actor_id)
                .setTimestamp(row.mutation_timestamp)
                .build()
        }

        val request = DeltaSyncRequest.newBuilder()
            .setNodeId(DEFAULT_NODE_ID)
            .setCheckpoint(SyncCheckpoint.newBuilder().build())
            .addAllOutgoingMutations(outgoingMutations)
            .build()

        val response = SyncServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(8, TimeUnit.SECONDS)
            .deltaSync(request)

        pending.forEach { row ->
            database.localQueries.markMutationSynced(row.mutation_id)
        }

        SyncResult(
            ok = true,
            syncedCount = pending.size,
            incomingCount = response.incomingMutationsCount,
            message = "Synced ${pending.size} mutation(s), received ${response.incomingMutationsCount} mutation(s).",
        )
    } catch (exception: StatusRuntimeException) {
        val hint = if (host == "10.0.2.2") {
            " If using a physical phone, set host to your laptop LAN IP."
        } else {
            ""
        }
        SyncResult(
            ok = false,
            syncedCount = 0,
            incomingCount = 0,
            message = "gRPC sync failed: ${exception.status.code.name}${hint}",
        )
    } catch (exception: Exception) {
        SyncResult(
            ok = false,
            syncedCount = 0,
            incomingCount = 0,
            message = "Sync failed: ${exception.message}",
        )
    } finally {
        channel.shutdownNow()
    }
}

private fun parseChangedFields(rawJson: String): Map<String, String> {
    return try {
        val obj = JSONObject(rawJson)
        obj.keys().asSequence().associateWith { key -> obj.opt(key).toString() }
    } catch (_: Exception) {
        mapOf("raw" to rawJson)
    }
}

private fun formatLastSync(epochMillis: Long?): String {
    if (epochMillis == null) {
        return "Never"
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    AndroidappTheme {
        DashboardScreen()
    }
}
