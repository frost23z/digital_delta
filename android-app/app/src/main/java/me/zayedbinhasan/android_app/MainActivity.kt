package me.zayedbinhasan.android_app

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import me.zayedbinhasan.android_app.data.local.db.LocalDatabaseFactory
import me.zayedbinhasan.data.Database
import me.zayedbinhasan.data.LocalQueries
import me.zayedbinhasan.android_app.ui.theme.AndroidappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalDeltaApp()
        }
    }
}

@Composable
fun DigitalDeltaApp() {
    AndroidappTheme {
        val context = LocalContext.current
        val database = remember { LocalDatabaseFactory.create(context.applicationContext) }
        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

        if (isAuthenticated) {
            AuthenticatedShell(
                database = database,
                onLogout = { isAuthenticated = false },
            )
        } else {
            LoginScreen(
                onLoginClick = { isAuthenticated = true },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DigitalDeltaAppPreview() {
    DigitalDeltaApp()
}

private data class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

private object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val DELIVERIES = "deliveries"
    const val ROUTE = "route"
    const val POD = "pod"
    const val SYNC_STATUS = "sync_status"
}

private const val DEFAULT_SYNC_PEER_ID = "sync-server-main"

private val appDestinations = listOf(
    AppDestination(route = AppRoutes.DASHBOARD, title = "Dashboard", icon = Icons.Filled.Dashboard),
    AppDestination(route = AppRoutes.DELIVERIES, title = "Deliveries", icon = Icons.Filled.Inventory2),
    AppDestination(route = AppRoutes.ROUTE, title = "Route", icon = Icons.Filled.Directions),
    AppDestination(route = AppRoutes.POD, title = "PoD", icon = Icons.Filled.QrCodeScanner),
    AppDestination(route = AppRoutes.SYNC_STATUS, title = "Sync Status", icon = Icons.Filled.Sync),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(onLoginClick: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Digital Delta Login") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Welcome to Digital Delta", fontWeight = FontWeight.Bold)
                    Text("Sign in to view delivery status, routing updates, proof-of-delivery records, and sync health.")
                }
            }
            Button(onClick = onLoginClick) {
                Text("Continue")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(database: Database, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: appDestinations.first().route
    val currentTitle = appDestinations.firstOrNull { it.route == currentRoute }?.title ?: "Digital Delta"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentTitle) },
                actions = {
                    Button(onClick = onLogout, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Logout")
                    }
                },
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = currentRoute)
        },
    ) { innerPadding ->
        AppNavHost(
            database = database,
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController, currentRoute: String) {
    NavigationBar {
        appDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(AppRoutes.DASHBOARD) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { Text(destination.title) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title,
                    )
                },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    database: Database,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(database = database)
        }
        composable(AppRoutes.DELIVERIES) {
            DeliveriesScreen(database = database)
        }
        composable(AppRoutes.ROUTE) {
            RoutesScreen(database = database)
        }
        composable(AppRoutes.POD) {
            PodScreen(database = database)
        }
        composable(AppRoutes.SYNC_STATUS) {
            SyncStatusScreen(database = database)
        }
    }
}

@Composable
private fun DashboardScreen(database: Database) {
    val queries = database.localQueries

    val users by remember(queries) {
        queries.selectAllUsers { userId, displayName, role, _, active, _, _ ->
            UserUi(userId = userId, displayName = displayName, role = role, active = active)
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val deliveries by remember(queries) {
        queries.selectAllDeliveries { taskId, _, quantity, originId, destinationId, _, _, assignedDriverId, status, _ ->
            DeliveryUi(
                taskId = taskId,
                quantity = quantity,
                originId = originId,
                destinationId = destinationId,
                status = status,
                assignedDriverId = assignedDriverId,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val routes by remember(queries) {
        queries.selectAllRoutes { routeId, _, _, vehicle, _, reasonCode, _ ->
            RouteUi(routeId = routeId, vehicle = vehicle, reasonCode = reasonCode)
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val receipts by remember(queries) {
        queries.selectAllReceipts { receiptId, deliveryId, _, _, _, _, _, _, verified, _ ->
            ReceiptUi(receiptId = receiptId, deliveryId = deliveryId, verified = verified)
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mission Overview", fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profiles: ${users.size}")
                Text("Deliveries: ${deliveries.size}")
                Text("Routes: ${routes.size}")
                Text("PoD Records: ${receipts.size}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Team Profile", fontWeight = FontWeight.Bold)
                val user = users.firstOrNull()
                if (user == null) {
                    Text("No local profile yet. Add a profile to enable offline role-based actions.")
                    Button(onClick = { insertDemoUser(queries) }) {
                        Text("Add Demo Profile")
                    }
                } else {
                    Text("${user.displayName} (${user.role})")
                    Text(if (user.active) "Status: Active" else "Status: Inactive")
                }
            }
        }
    }
}

@Composable
private fun DeliveriesScreen(database: Database) {
    val queries = database.localQueries
    var selectedDeliveryId by rememberSaveable { mutableStateOf<String?>(null) }

    val deliveries by remember(queries) {
        queries.selectAllDeliveries { taskId, supplyId, quantity, originId, destinationId, priority, deadlineTimestamp, assignedDriverId, status, updatedAt ->
            DeliveryFullUi(
                taskId = taskId,
                supplyId = supplyId,
                quantity = quantity,
                originId = originId,
                destinationId = destinationId,
                priority = priority,
                deadlineTimestamp = deadlineTimestamp,
                assignedDriverId = assignedDriverId,
                status = status,
                updatedAt = updatedAt,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val selected = deliveries.firstOrNull { it.taskId == selectedDeliveryId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Deliveries", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoDelivery(queries) }) {
            Text("Create Delivery")
        }

        if (selected != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Selected Delivery", fontWeight = FontWeight.Bold)
                    Text("Task: ${selected.taskId}")
                    Text("From ${selected.originId} to ${selected.destinationId}")
                    Text("Quantity: ${selected.quantity}, Priority: ${selected.priority}")
                    Text("Status: ${selected.status}")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(deliveries, key = { it.taskId }) { delivery ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${delivery.taskId} • ${delivery.status}", fontWeight = FontWeight.Bold)
                        Text("${delivery.originId} -> ${delivery.destinationId}")
                        Text("Qty ${delivery.quantity}, Priority ${delivery.priority}")
                        Button(onClick = { selectedDeliveryId = delivery.taskId }) {
                            Text("View Details")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesScreen(database: Database) {
    val queries = database.localQueries

    val routes by remember(queries) {
        queries.selectAllRoutes { routeId, edgeIdsJson, totalDurationMins, vehicle, etaTimestamp, reasonCode, _ ->
            RouteFullUi(
                routeId = routeId,
                edgeIds = edgeIdsJson,
                totalDurationMins = totalDurationMins,
                vehicle = vehicle,
                etaTimestamp = etaTimestamp,
                reasonCode = reasonCode,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routes", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoRoute(queries) }) {
            Text("Create Route")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routes, key = { it.routeId }) { route ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${route.routeId} • ${route.vehicle}", fontWeight = FontWeight.Bold)
                        Text("ETA: ${route.etaTimestamp}")
                        Text("Duration: ${route.totalDurationMins} mins")
                        Text("Reason: ${route.reasonCode}")
                        Text("Edges: ${route.edgeIds}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PodScreen(database: Database) {
    val queries = database.localQueries

    val receipts by remember(queries) {
        queries.selectAllReceipts { receiptId, deliveryId, senderUserId, recipientUserId, _, nonce, _, _, verified, verifiedAt ->
            ReceiptFullUi(
                receiptId = receiptId,
                deliveryId = deliveryId,
                senderUserId = senderUserId,
                recipientUserId = recipientUserId,
                nonce = nonce,
                verified = verified,
                verifiedAt = verifiedAt,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Proof of Delivery", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoReceipt(queries) }) {
            Text("Create Receipt")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(receipts, key = { it.receiptId }) { receipt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${receipt.receiptId} • Delivery ${receipt.deliveryId}", fontWeight = FontWeight.Bold)
                        Text("Sender: ${receipt.senderUserId} -> Recipient: ${receipt.recipientUserId}")
                        Text("Nonce: ${receipt.nonce}")
                        Text(if (receipt.verified) "Verification: Accepted" else "Verification: Pending")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusScreen(database: Database) {
    val queries = database.localQueries
    val peerId = DEFAULT_SYNC_PEER_ID

    val pendingMutations by remember(queries) {
        queries.selectPendingMutations { mutationId, entityType, entityId, operationType, _, _, _, deviceId, mutationTimestamp, _ ->
            MutationUi(
                mutationId = mutationId,
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                deviceId = deviceId,
                mutationTimestamp = mutationTimestamp,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val latestMutationTimestamp by remember(queries) {
        queries.selectLatestMutationTimestamp().asFlow().mapToOneOrNull(Dispatchers.IO)
    }.collectAsState(initial = null)

    val checkpoints by remember(queries) {
        queries.selectAllSyncCheckpoints { checkpointPeerId, lastSeenCounter, lastSyncTimestamp, updatedAt ->
            SyncCheckpointUi(
                peerId = checkpointPeerId,
                lastSeenCounter = lastSeenCounter,
                lastSyncTimestamp = lastSyncTimestamp,
                updatedAt = updatedAt,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

    val unseenForPeer by remember(queries, peerId) {
        queries.selectUnseenMutationsForPeer(peerId) { mutationId, entityType, entityId, operationType, _, _, _, deviceId, mutationTimestamp, _ ->
            MutationUi(
                mutationId = mutationId,
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                deviceId = deviceId,
                mutationTimestamp = mutationTimestamp,
            )
        }.asFlow().mapToList(Dispatchers.IO)
    }.collectAsState(initial = emptyList())

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
                Button(
                    onClick = {
                        simulateSyncWithPeer(
                            queries = queries,
                            peerId = peerId,
                            appliedMutationCount = unseenForPeer.size,
                        )
                    },
                ) {
                    Text("Simulate Sync With Peer")
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

private fun insertDemoUser(queries: LocalQueries) {
    val now = System.currentTimeMillis()
    val userId = "user-${UUID.randomUUID().toString().take(8)}"
    queries.upsertUser(
        user_id = userId,
        display_name = "Field Volunteer",
        role = "FIELD_VOLUNTEER",
        public_key = "pk_demo_$userId",
        active = true,
        created_at = now,
        updated_at = now,
    )
    appendMutation(queries, entityType = "user", entityId = userId, operationType = "UPSERT")
}

private fun insertDemoDelivery(queries: LocalQueries) {
    val now = System.currentTimeMillis()
    val deliveryId = "task-${UUID.randomUUID().toString().take(8)}"
    queries.upsertDelivery(
        task_id = deliveryId,
        supply_id = "medical-kit-a",
        quantity = 25,
        origin_id = "warehouse-01",
        destination_id = "camp-03",
        priority = "P1_HIGH",
        deadline_timestamp = now + 3_600_000,
        assigned_driver_id = "driver-07",
        status = "PENDING",
        updated_at = now,
    )
    appendMutation(queries, entityType = "delivery", entityId = deliveryId, operationType = "UPSERT")
}

private fun insertDemoRoute(queries: LocalQueries) {
    val now = System.currentTimeMillis()
    val routeId = "route-${UUID.randomUUID().toString().take(8)}"
    queries.upsertRoute(
        route_id = routeId,
        edge_ids_json = "edge-1,edge-2,edge-9",
        total_duration_mins = 42,
        vehicle = "TRUCK",
        eta_timestamp = now + 2_520_000,
        reason_code = "NORMAL_FLOW",
        updated_at = now,
    )
    appendMutation(queries, entityType = "route", entityId = routeId, operationType = "UPSERT")
}

private fun insertDemoReceipt(queries: LocalQueries) {
    val now = System.currentTimeMillis()
    val receiptId = "receipt-${UUID.randomUUID().toString().take(8)}"
    queries.upsertReceipt(
        receipt_id = receiptId,
        delivery_id = "task-demo",
        sender_user_id = "sender-01",
        recipient_user_id = "recipient-02",
        payload_hash = "hash_demo_$receiptId",
        nonce = now,
        sender_signature = "sig_sender_$receiptId",
        recipient_signature = "sig_recipient_$receiptId",
        verified = true,
        verified_at = now,
    )
    appendMutation(queries, entityType = "receipt", entityId = receiptId, operationType = "UPSERT")
}

private fun appendMutation(
    queries: LocalQueries,
    entityType: String,
    entityId: String,
    operationType: String,
) {
    val deviceId = "android_client"
    val nextCounter = queries.selectLocalDeviceMutationCount(deviceId).executeAsOne() + 1
    val vectorClockJson = "{\"$deviceId\":$nextCounter}"

    queries.insertMutation(
        mutation_id = "mut-${UUID.randomUUID().toString().take(12)}",
        entity_type = entityType,
        entity_id = entityId,
        operation_type = operationType,
        changed_fields_json = "{}",
        vector_clock_json = vectorClockJson,
        actor_id = "ui_user",
        device_id = deviceId,
        mutation_timestamp = System.currentTimeMillis(),
        synced = false,
    )
}

private fun simulateSyncWithPeer(
    queries: LocalQueries,
    peerId: String,
    appliedMutationCount: Int,
) {
    val now = System.currentTimeMillis()
    val existingCheckpoint = queries.selectSyncCheckpointByPeer(peerId).executeAsOneOrNull()
    val latestMutationTimestamp = queries.selectLatestMutationTimestamp().executeAsOneOrNull() ?: now
    val nextCounter = (existingCheckpoint?.last_seen_counter ?: 0L) + appliedMutationCount.toLong()

    if (appliedMutationCount > 0) {
        queries.markAllMutationsSynced()
    }

    queries.upsertSyncCheckpoint(
        peer_id = peerId,
        last_seen_counter = nextCounter,
        last_sync_timestamp = latestMutationTimestamp,
        updated_at = now,
    )
}

private data class UserUi(
    val userId: String,
    val displayName: String,
    val role: String,
    val active: Boolean,
)

private data class DeliveryUi(
    val taskId: String,
    val quantity: Long,
    val originId: String,
    val destinationId: String,
    val status: String,
    val assignedDriverId: String?,
)

private data class DeliveryFullUi(
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

private data class RouteUi(
    val routeId: String,
    val vehicle: String,
    val reasonCode: String,
)

private data class RouteFullUi(
    val routeId: String,
    val edgeIds: String,
    val totalDurationMins: Long,
    val vehicle: String,
    val etaTimestamp: Long,
    val reasonCode: String,
)

private data class ReceiptUi(
    val receiptId: String,
    val deliveryId: String,
    val verified: Boolean,
)

private data class ReceiptFullUi(
    val receiptId: String,
    val deliveryId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val nonce: Long,
    val verified: Boolean,
    val verifiedAt: Long?,
)

private data class MutationUi(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val deviceId: String,
    val mutationTimestamp: Long,
)

private data class SyncCheckpointUi(
    val peerId: String,
    val lastSeenCounter: Long,
    val lastSyncTimestamp: Long,
    val updatedAt: Long,
)