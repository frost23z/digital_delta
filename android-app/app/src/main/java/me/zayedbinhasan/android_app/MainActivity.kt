package me.zayedbinhasan.android_app

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
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
import androidx.compose.material.icons.filled.ReportProblem
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
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.data.Database
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
    const val CONFLICTS = "conflicts"
}

private const val DEFAULT_SYNC_PEER_ID = "sync-server-main"

private val appDestinations = listOf(
    AppDestination(route = AppRoutes.DASHBOARD, title = "Dashboard", icon = Icons.Filled.Dashboard),
    AppDestination(route = AppRoutes.DELIVERIES, title = "Deliveries", icon = Icons.Filled.Inventory2),
    AppDestination(route = AppRoutes.ROUTE, title = "Route", icon = Icons.Filled.Directions),
    AppDestination(route = AppRoutes.POD, title = "PoD", icon = Icons.Filled.QrCodeScanner),
    AppDestination(route = AppRoutes.SYNC_STATUS, title = "Sync Status", icon = Icons.Filled.Sync),
    AppDestination(route = AppRoutes.CONFLICTS, title = "Conflicts", icon = Icons.Filled.ReportProblem),
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
    val repository = remember(database) { LocalRepository(database.localQueries) }
    val openConflictCount by remember(repository) {
        repository.observeOpenConflictCount()
    }.collectAsState(initial = 0L)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: appDestinations.first().route
    val currentTitle = appDestinations.firstOrNull { it.route == currentRoute }?.title ?: "Digital Delta"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentTitle) },
                actions = {
                    if (openConflictCount > 0) {
                        Text(
                            text = "Conflicts: $openConflictCount",
                            modifier = Modifier.padding(end = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(onClick = onLogout, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Logout")
                    }
                },
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentRoute = currentRoute,
                openConflictCount = openConflictCount,
            )
        },
    ) { innerPadding ->
        AppNavHost(
            repository = repository,
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String,
    openConflictCount: Long,
) {
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
                label = {
                    val label = if (destination.route == AppRoutes.CONFLICTS && openConflictCount > 0) {
                        "${destination.title} (${openConflictCount})"
                    } else {
                        destination.title
                    }
                    Text(label)
                },
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
    repository: LocalRepository,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(repository = repository)
        }
        composable(AppRoutes.DELIVERIES) {
            DeliveriesScreen(repository = repository)
        }
        composable(AppRoutes.ROUTE) {
            RoutesScreen(repository = repository)
        }
        composable(AppRoutes.POD) {
            PodScreen(repository = repository)
        }
        composable(AppRoutes.SYNC_STATUS) {
            SyncStatusScreen(repository = repository)
        }
        composable(AppRoutes.CONFLICTS) {
            ConflictScreen(repository = repository)
        }
    }
}

@Composable
private fun DashboardScreen(repository: LocalRepository) {
    val usersRaw by remember(repository) {
        repository.observeUsers()
    }.collectAsState(initial = emptyList())

    val deliveriesRaw by remember(repository) {
        repository.observeDeliveries()
    }.collectAsState(initial = emptyList())

    val routesRaw by remember(repository) {
        repository.observeRoutes()
    }.collectAsState(initial = emptyList())

    val receiptsRaw by remember(repository) {
        repository.observeReceipts()
    }.collectAsState(initial = emptyList())

    val users = usersRaw.map { row ->
        UserUi(
            userId = row.user_id,
            displayName = row.display_name,
            role = row.role,
            active = row.active,
        )
    }

    val deliveries = deliveriesRaw.map { row ->
        DeliveryUi(
            taskId = row.task_id,
            quantity = row.quantity,
            originId = row.origin_id,
            destinationId = row.destination_id,
            status = row.status,
            assignedDriverId = row.assigned_driver_id,
        )
    }

    val routes = routesRaw.map { row ->
        RouteUi(routeId = row.route_id, vehicle = row.vehicle, reasonCode = row.reason_code)
    }

    val receipts = receiptsRaw.map { row ->
        ReceiptUi(receiptId = row.receipt_id, deliveryId = row.delivery_id, verified = row.verified)
    }

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
                    Button(onClick = { insertDemoUser(repository) }) {
                        Text("Add Demo Profile")
                    }
                } else {
                    Text("${user.displayName} (${user.role})")
                    Text(if (user.active) "Status: Active" else "Status: Inactive")
                    Button(onClick = { deleteUser(repository, user.userId) }) {
                        Text("Delete Profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveriesScreen(repository: LocalRepository) {
    var selectedDeliveryId by rememberSaveable { mutableStateOf<String?>(null) }

    val deliveriesRaw by remember(repository) {
        repository.observeDeliveries()
    }.collectAsState(initial = emptyList())

    val deliveries = deliveriesRaw.map { row ->
        DeliveryFullUi(
            taskId = row.task_id,
            supplyId = row.supply_id,
            quantity = row.quantity,
            originId = row.origin_id,
            destinationId = row.destination_id,
            priority = row.priority,
            deadlineTimestamp = row.deadline_timestamp,
            assignedDriverId = row.assigned_driver_id,
            status = row.status,
            updatedAt = row.updated_at,
        )
    }

    val selected = deliveries.firstOrNull { it.taskId == selectedDeliveryId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Deliveries", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoDelivery(repository) }) {
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
                        Button(onClick = { deleteDelivery(repository, delivery.taskId) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesScreen(repository: LocalRepository) {
    val routesRaw by remember(repository) {
        repository.observeRoutes()
    }.collectAsState(initial = emptyList())

    val routes = routesRaw.map { row ->
        RouteFullUi(
            routeId = row.route_id,
            edgeIds = row.edge_ids_json,
            totalDurationMins = row.total_duration_mins,
            vehicle = row.vehicle,
            etaTimestamp = row.eta_timestamp,
            reasonCode = row.reason_code,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routes", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoRoute(repository) }) {
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
                        Button(onClick = { deleteRoute(repository, route.routeId) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodScreen(repository: LocalRepository) {
    val receiptsRaw by remember(repository) {
        repository.observeReceipts()
    }.collectAsState(initial = emptyList())

    val receipts = receiptsRaw.map { row ->
        ReceiptFullUi(
            receiptId = row.receipt_id,
            deliveryId = row.delivery_id,
            senderUserId = row.sender_user_id,
            recipientUserId = row.recipient_user_id,
            nonce = row.nonce,
            verified = row.verified,
            verifiedAt = row.verified_at,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Proof of Delivery", fontWeight = FontWeight.Bold)
        Button(onClick = { insertDemoReceipt(repository) }) {
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
                        Button(onClick = { deleteReceipt(repository, receipt.receiptId) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusScreen(repository: LocalRepository) {
    val peerId = DEFAULT_SYNC_PEER_ID

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
                Button(
                    onClick = {
                        simulateSyncWithPeer(
                            repository = repository,
                            peerId = peerId,
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

@Composable
private fun ConflictScreen(repository: LocalRepository) {
    val conflictsRaw by remember(repository) {
        repository.observeOpenConflicts()
    }.collectAsState(initial = emptyList())

    val conflicts = conflictsRaw.map { row ->
        ConflictUi(
            conflictId = row.conflict_id,
            entityType = row.entity_type,
            entityId = row.entity_id,
            fieldName = row.field_name,
            localValue = row.local_value,
            remoteValue = row.remote_value,
            mergeStrategy = row.merge_strategy,
            manualRequired = row.manual_required,
            createdAt = row.created_at,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Conflict Resolution", fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open Conflicts: ${conflicts.size}")
                Text("Merge rules: LWW for scalars, additive for quantities, manual for ownership")
                Button(onClick = { simulateRemoteStatusMerge(repository) }) {
                    Text("Simulate LWW Status Merge")
                }
                Button(onClick = { simulateRemoteQuantityMerge(repository) }) {
                    Text("Simulate Additive Quantity Merge")
                }
                Button(onClick = { simulateRemoteOwnershipConflict(repository) }) {
                    Text("Simulate Ownership Conflict")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(conflicts, key = { it.conflictId }) { conflict ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${conflict.entityType}:${conflict.entityId}", fontWeight = FontWeight.Bold)
                        Text("Field: ${conflict.fieldName}")
                        Text("Strategy: ${conflict.mergeStrategy}")
                        Text("Local: ${conflict.localValue ?: "(null)"}")
                        Text("Remote: ${conflict.remoteValue ?: "(null)"}")
                        Text(if (conflict.manualRequired) "Badge: CONFLICT_DETECTED" else "Badge: AUTO_MERGED")
                        Button(onClick = { resolveConflictAction(repository, conflict, "ACCEPT_LOCAL") }) {
                            Text("Accept Local")
                        }
                        Button(onClick = { resolveConflictAction(repository, conflict, "ACCEPT_REMOTE") }) {
                            Text("Accept Remote")
                        }
                        Button(onClick = { resolveConflictAction(repository, conflict, "MERGE_MANUAL") }) {
                            Text("Merge Manually")
                        }
                    }
                }
            }
        }
    }
}

private fun insertDemoUser(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val userId = "user-${UUID.randomUUID().toString().take(8)}"
    repository.upsertUser(
        userId = userId,
        displayName = "Field Volunteer",
        role = "FIELD_VOLUNTEER",
        publicKey = "pk_demo_$userId",
        active = true,
        createdAt = now,
        updatedAt = now,
    )
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "UPSERT")
}

private fun deleteUser(repository: LocalRepository, userId: String) {
    repository.deleteUserById(userId)
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "DELETE")
}

private fun insertDemoDelivery(repository: LocalRepository) {
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

private fun deleteDelivery(repository: LocalRepository, taskId: String) {
    repository.deleteDeliveryById(taskId)
    appendMutation(repository, entityType = "delivery", entityId = taskId, operationType = "DELETE")
}

private fun insertDemoRoute(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val routeId = "route-${UUID.randomUUID().toString().take(8)}"
    repository.upsertRoute(
        routeId = routeId,
        edgeIdsJson = "edge-1,edge-2,edge-9",
        totalDurationMins = 42,
        vehicle = "TRUCK",
        etaTimestamp = now + 2_520_000,
        reasonCode = "NORMAL_FLOW",
        updatedAt = now,
    )
    appendMutation(repository, entityType = "route", entityId = routeId, operationType = "UPSERT")
}

private fun deleteRoute(repository: LocalRepository, routeId: String) {
    repository.deleteRouteById(routeId)
    appendMutation(repository, entityType = "route", entityId = routeId, operationType = "DELETE")
}

private fun insertDemoReceipt(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val receiptId = "receipt-${UUID.randomUUID().toString().take(8)}"
    repository.upsertReceipt(
        receiptId = receiptId,
        deliveryId = "task-demo",
        senderUserId = "sender-01",
        recipientUserId = "recipient-02",
        payloadHash = "hash_demo_$receiptId",
        nonce = now,
        senderSignature = "sig_sender_$receiptId",
        recipientSignature = "sig_recipient_$receiptId",
        verified = true,
        verifiedAt = now,
    )
    appendMutation(repository, entityType = "receipt", entityId = receiptId, operationType = "UPSERT")
}

private fun deleteReceipt(repository: LocalRepository, receiptId: String) {
    repository.deleteReceiptById(receiptId)
    appendMutation(repository, entityType = "receipt", entityId = receiptId, operationType = "DELETE")
}

private fun appendMutation(
    repository: LocalRepository,
    entityType: String,
    entityId: String,
    operationType: String,
) {
    val deviceId = "android_client"
    val nextCounter = repository.localDeviceMutationCount(deviceId) + 1
    val vectorClockJson = "{\"$deviceId\":$nextCounter}"

    repository.insertMutation(
        mutationId = "mut-${UUID.randomUUID().toString().take(12)}",
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        changedFieldsJson = "{}",
        vectorClockJson = vectorClockJson,
        actorId = "ui_user",
        deviceId = deviceId,
        mutationTimestamp = System.currentTimeMillis(),
        synced = false,
    )
}

private fun simulateRemoteStatusMerge(repository: LocalRepository) {
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

private fun simulateRemoteQuantityMerge(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: return
    val remoteDelta = 5L
    val mergedQuantity = (delivery.quantity + remoteDelta).coerceAtLeast(0)
    repository.updateDeliveryQuantity(delivery.task_id, mergedQuantity, System.currentTimeMillis())
    appendMutation(repository, entityType = "delivery", entityId = delivery.task_id, operationType = "MERGE_ADDITIVE")
}

private fun simulateRemoteOwnershipConflict(repository: LocalRepository) {
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
        entityType = "delivery",
        entityId = delivery.task_id,
        fieldName = "assigned_driver_id",
        localValue = localAssignee,
        remoteValue = remoteAssignee,
        mergeStrategy = "MANUAL_OWNERSHIP",
        manualRequired = true,
    )
}

private fun createConflict(
    repository: LocalRepository,
    entityType: String,
    entityId: String,
    fieldName: String,
    localValue: String?,
    remoteValue: String?,
    mergeStrategy: String,
    manualRequired: Boolean,
) {
    val now = System.currentTimeMillis()
    val conflictId = "conf-${UUID.randomUUID().toString().take(12)}"
    repository.insertConflict(
        conflictId = conflictId,
        entityType = entityType,
        entityId = entityId,
        fieldName = fieldName,
        localValue = localValue,
        remoteValue = remoteValue,
        mergeStrategy = mergeStrategy,
        manualRequired = manualRequired,
        status = "OPEN",
        createdAt = now,
        resolvedAt = null,
        resolution = null,
    )
    appendMutation(repository, entityType = "conflict", entityId = conflictId, operationType = "OPEN")
}

private fun resolveConflictAction(
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

private fun simulateSyncWithPeer(
    repository: LocalRepository,
    peerId: String,
    appliedMutationCount: Int,
) {
    val now = System.currentTimeMillis()
    val existingCheckpoint = repository.syncCheckpointByPeer(peerId)
    val latestMutationTimestamp = repository.latestMutationTimestampNow() ?: now
    val nextCounter = (existingCheckpoint?.last_seen_counter ?: 0L) + appliedMutationCount.toLong()

    if (appliedMutationCount > 0) {
        repository.markAllMutationsSynced()
    }

    repository.upsertSyncCheckpoint(
        peerId = peerId,
        lastSeenCounter = nextCounter,
        lastSyncTimestamp = latestMutationTimestamp,
        updatedAt = now,
    )
    appendMutation(repository, entityType = "sync_checkpoint", entityId = peerId, operationType = "UPSERT")
}

private fun applyIncomingMutationBatch(repository: LocalRepository) {
    val delivery = repository.firstDeliveryOrNull() ?: run {
        insertDemoDelivery(repository)
        repository.firstDeliveryOrNull()
    } ?: return

    val incoming = listOf(
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "status",
            remoteValue = "IN_TRANSIT",
            mergeStrategy = "LWW",
            timestamp = System.currentTimeMillis(),
        ),
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "quantity",
            remoteValue = "5",
            mergeStrategy = "ADDITIVE",
            timestamp = System.currentTimeMillis(),
        ),
        IncomingMutation(
            entityType = "delivery",
            entityId = delivery.task_id,
            fieldName = "assigned_driver_id",
            remoteValue = "driver-remote",
            mergeStrategy = "MANUAL_OWNERSHIP",
            timestamp = System.currentTimeMillis(),
        ),
    )

    applyIncomingMutations(repository, incoming)
}

private fun applyIncomingMutations(repository: LocalRepository, incoming: List<IncomingMutation>) {
    incoming.forEach { mutation ->
        if (mutation.entityType != "delivery") {
            return@forEach
        }

        val local = repository.deliveryById(mutation.entityId) ?: return@forEach

        when (mutation.fieldName) {
            "status" -> {
                val remoteStatus = mutation.remoteValue
                if (mutation.timestamp > local.updated_at) {
                    repository.updateDeliveryStatus(local.task_id, remoteStatus, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_STATUS")
                } else if (mutation.timestamp == local.updated_at) {
                    val chosen = maxOf(local.status, remoteStatus)
                    repository.updateDeliveryStatus(local.task_id, chosen, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_TIE_BREAK")
                }
            }
            "quantity" -> {
                val delta = mutation.remoteValue.toLongOrNull() ?: return@forEach
                val merged = (local.quantity + delta).coerceAtLeast(0)
                repository.updateDeliveryQuantity(local.task_id, merged, mutation.timestamp)
                appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_QUANTITY")
            }
            "assigned_driver_id" -> {
                val localAssignee = local.assigned_driver_id
                if (localAssignee == null || localAssignee == mutation.remoteValue) {
                    repository.updateDeliveryAssignee(local.task_id, mutation.remoteValue, mutation.timestamp)
                    appendMutation(repository, entityType = "delivery", entityId = local.task_id, operationType = "APPLY_REMOTE_ASSIGNMENT")
                } else {
                    createConflict(
                        repository = repository,
                        entityType = "delivery",
                        entityId = local.task_id,
                        fieldName = "assigned_driver_id",
                        localValue = localAssignee,
                        remoteValue = mutation.remoteValue,
                        mergeStrategy = mutation.mergeStrategy,
                        manualRequired = true,
                    )
                }
            }
        }
    }
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

private data class IncomingMutation(
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val remoteValue: String,
    val mergeStrategy: String,
    val timestamp: Long,
)

private data class SyncCheckpointUi(
    val peerId: String,
    val lastSeenCounter: Long,
    val lastSyncTimestamp: Long,
    val updatedAt: Long,
)

private data class ConflictUi(
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