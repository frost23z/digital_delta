package me.zayedbinhasan.android_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.auth.OfflineAuthSession
import me.zayedbinhasan.android_app.data.local.db.LocalDatabaseFactory
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.theme.AndroidappTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DigitalDeltaApp() {
    AndroidappTheme {
        val context = LocalContext.current
        val database = remember { LocalDatabaseFactory.create(context.applicationContext) }
        val repository = remember(database) { LocalRepository(database.localQueries) }
        val authManager = remember(repository) {
            OfflineAuthManager(context.applicationContext, repository)
        }
        val restoredSession = remember(authManager) { authManager.restoreActiveSession() }

        var activeUserId by rememberSaveable { mutableStateOf(restoredSession?.userId) }
        var activeRole by rememberSaveable { mutableStateOf(restoredSession?.role) }
        var activeAuthMode by rememberSaveable { mutableStateOf(restoredSession?.authMode ?: "SIGNED_OUT") }

        val activeSession = if (activeUserId != null && activeRole != null) {
            OfflineAuthSession(
                userId = activeUserId!!,
                role = activeRole!!,
                authMode = activeAuthMode,
            )
        } else {
            null
        }

        if (activeSession != null) {
            AuthenticatedShell(
                repository = repository,
                authManager = authManager,
                session = activeSession,
                onLogout = {
                    authManager.clearActiveSession()
                    activeUserId = null
                    activeRole = null
                    activeAuthMode = "SIGNED_OUT"
                },
            )
        } else {
            LoginScreen(
                authManager = authManager,
                onLoginSuccess = { session ->
                    activeUserId = session.userId
                    activeRole = session.role
                    activeAuthMode = session.authMode
                },
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

internal const val DEFAULT_SYNC_PEER_ID = "sync-server-main"
internal const val DEFAULT_SYNC_HTTP_BASE_URL = "http://10.0.2.2:8081"
internal const val DEFAULT_LOCAL_NODE_ID = "android_client"

private val appDestinations = listOf(
    AppDestination(route = AppRoutes.DASHBOARD, title = "Dashboard", icon = Icons.Filled.Dashboard),
    AppDestination(route = AppRoutes.DELIVERIES, title = "Deliveries", icon = Icons.Filled.Inventory2),
    AppDestination(route = AppRoutes.ROUTE, title = "Route", icon = Icons.Filled.Directions),
    AppDestination(route = AppRoutes.POD, title = "PoD", icon = Icons.Filled.QrCodeScanner),
    AppDestination(route = AppRoutes.SYNC_STATUS, title = "Sync Status", icon = Icons.Filled.Sync),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    authManager: OfflineAuthManager,
    onLoginSuccess: (OfflineAuthSession) -> Unit,
) {
    var identifierInput by rememberSaveable { mutableStateOf("") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var cachedIdentityUserId by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.userId ?: "") }
    var cachedIdentityRole by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.role ?: "") }
    var cachedIdentityPublicKey by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.publicKey ?: "") }
    var otpPreview by rememberSaveable { mutableStateOf(authManager.generateCurrentOtp()?.code ?: "") }
    var otpExpiresIn by rememberSaveable { mutableLongStateOf(authManager.generateCurrentOtp()?.expiresInSeconds ?: 0L) }
    var offlineAuthState by rememberSaveable {
        mutableStateOf(if (cachedIdentityUserId.isNotEmpty()) "IDENTITY_CACHED" else "NO_IDENTITY")
    }
    var sessionStatus by rememberSaveable {
        mutableStateOf(if (authManager.restoreActiveSession() != null) "SESSION_AVAILABLE" else "SIGNED_OUT")
    }

    LaunchedEffect(Unit) {
        val cached = authManager.cachedIdentity()
        cachedIdentityUserId = cached?.userId ?: ""
        cachedIdentityRole = cached?.role ?: ""
        cachedIdentityPublicKey = cached?.publicKey ?: ""

        val otp = authManager.generateCurrentOtp()
        otpPreview = otp?.code ?: ""
        otpExpiresIn = otp?.expiresInSeconds ?: 0L

        offlineAuthState = if (cached != null) "IDENTITY_CACHED" else "NO_IDENTITY"
        sessionStatus = if (authManager.restoreActiveSession() != null) "SESSION_AVAILABLE" else "SIGNED_OUT"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Digital Delta Login") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Welcome to Digital Delta", fontWeight = FontWeight.Bold)
                    Text("Provision once, then re-login offline with local OTP and cached role.")
                    Text("Offline Auth State: $offlineAuthState")
                    Text("Session Status: $sessionStatus")
                    if (cachedIdentityRole.isNotEmpty()) {
                        Text("Role Badge: $cachedIdentityRole", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = identifierInput,
                onValueChange = { identifierInput = it },
                label = { Text("User ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = otpInput,
                onValueChange = { otpInput = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("TOTP (6 digits)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (otpPreview.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Local OTP Preview: $otpPreview")
                        Text("Expires In: ${otpExpiresIn}s")
                        if (cachedIdentityPublicKey.isNotEmpty()) {
                            Text("Device Public Key (cached): ${cachedIdentityPublicKey.take(24)}...")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val userId = identifierInput.ifEmpty { cachedIdentityUserId.ifEmpty { "field-volunteer" } }
                    val identity = authManager.provisionIdentity(
                        userId = userId,
                        role = cachedIdentityRole.ifEmpty { "FIELD_VOLUNTEER" },
                    )
                    if (identity != null) {
                        cachedIdentityUserId = identity.userId
                        cachedIdentityRole = identity.role
                        cachedIdentityPublicKey = identity.publicKey
                        identifierInput = identity.userId
                        val otp = authManager.generateCurrentOtp()
                        otpPreview = otp?.code ?: ""
                        otpExpiresIn = otp?.expiresInSeconds ?: 0L
                        offlineAuthState = "OTP_REQUIRED"
                        sessionStatus = "PROVISIONED"
                    } else {
                        offlineAuthState = "PROVISION_FAILED"
                        sessionStatus = "KEYSTORE_OR_STORAGE_ERROR"
                    }
                },
            ) {
                Text("Provision Identity (Offline Ready)")
            }

            Button(
                onClick = {
                    val otp = authManager.generateCurrentOtp()
                    otpPreview = otp?.code ?: ""
                    otpExpiresIn = otp?.expiresInSeconds ?: 0L
                    if (otp != null) {
                        offlineAuthState = "OTP_REQUIRED"
                        sessionStatus = "OTP_REFRESHED"
                    } else {
                        offlineAuthState = "NO_IDENTITY"
                        sessionStatus = "OTP_NOT_AVAILABLE"
                    }
                },
                enabled = cachedIdentityUserId.isNotEmpty(),
            ) {
                Text("Regenerate Local OTP")
            }

            Button(
                onClick = {
                    if (otpPreview.length == 6) {
                        otpInput = otpPreview
                        sessionStatus = "OTP_APPLIED"
                    }
                },
                enabled = otpPreview.length == 6,
            ) {
                Text("Use OTP Preview")
            }

            Button(
                onClick = {
                    val candidateOtp = otpInput.ifEmpty { otpPreview }
                    val session = authManager.loginOffline(
                        userId = identifierInput.ifEmpty { cachedIdentityUserId },
                        otp = candidateOtp,
                    )
                    if (session != null) {
                        onLoginSuccess(session)
                    } else {
                        val lockoutRemaining = authManager.lockoutRemainingSeconds()
                        if (lockoutRemaining > 0L) {
                            offlineAuthState = "AUTH_LOCKED"
                            sessionStatus = "LOCKED_${lockoutRemaining}s"
                        } else {
                            offlineAuthState = "AUTH_FAILED"
                            sessionStatus = "OTP_OR_IDENTITY_INVALID"
                        }
                    }
                },
                enabled = cachedIdentityUserId.isNotEmpty() && (otpInput.length == 6 || otpPreview.length == 6),
            ) {
                Text("Login Offline")
            }

            Button(
                onClick = {
                    val session = authManager.restoreActiveSession()
                    if (session != null) {
                        onLoginSuccess(session)
                    } else {
                        offlineAuthState = "NO_ACTIVE_SESSION"
                        sessionStatus = "NO_ACTIVE_SESSION"
                    }
                },
                enabled = authManager.restoreActiveSession() != null,
            ) {
                Text("Continue Cached Session")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    session: OfflineAuthSession,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val openConflictCount by remember(repository) {
        repository.observeOpenConflictCount()
    }.collectAsState(initial = 0L)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: appDestinations.first().route
    val currentTitle = when (currentRoute) {
        AppRoutes.CONFLICTS -> "Conflicts"
        else -> appDestinations.firstOrNull { it.route == currentRoute }?.title ?: "Digital Delta"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(AppRoutes.CONFLICTS) {
                                launchSingleTop = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReportProblem,
                            contentDescription = "Open conflicts",
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
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionStatusHeader(
                role = session.role,
                authMode = session.authMode,
                openConflictCount = openConflictCount,
            )
            AppNavHost(
                repository = repository,
                authManager = authManager,
                navController = navController,
                activeUserId = session.userId,
                activeRole = session.role,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String,
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
private fun SessionStatusHeader(
    role: String,
    authMode: String,
    openConflictCount: Long,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Role: ${role.replace('_', ' ')}", fontWeight = FontWeight.Bold)
            Text("Auth: ${authMode.replace('_', ' ')}")
            Text("Open conflicts: $openConflictCount")
        }
    }
}

@Composable
private fun AppNavHost(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    navController: NavHostController,
    activeUserId: String,
    activeRole: String,
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
            RoutesScreen(
                repository = repository,
                activeRole = activeRole,
            )
        }
        composable(AppRoutes.POD) {
            PodScreen(
                repository = repository,
                authManager = authManager,
                activeUserId = activeUserId,
            )
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
private fun RoutesScreen(
    repository: LocalRepository,
    activeRole: String,
) {
    val routesRaw by remember(repository) {
        repository.observeRoutes()
    }.collectAsState(initial = emptyList())
    val canManageRoutes = canManageRouteActions(activeRole)

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
        if (!canManageRoutes) {
            Text("Route management requires MANAGER/COMMANDER/ADMIN role")
        }
        Button(onClick = { insertDemoRoute(repository) }, enabled = canManageRoutes) {
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
                        Button(onClick = { deleteRoute(repository, route.routeId) }, enabled = canManageRoutes) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodScreen(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    activeUserId: String,
) {
    val receiptsRaw by remember(repository) {
        repository.observeReceipts()
    }.collectAsState(initial = emptyList())
    var podStatus by rememberSaveable { mutableStateOf("Idle") }
    var lastAcceptedNonce by rememberSaveable { mutableLongStateOf(0L) }
    val recipientUserId = "recipient-02"

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
        Text("Sender: $activeUserId -> Recipient: $recipientUserId")
        Text("PoD State: $podStatus")

        Button(
            onClick = {
                val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                val result = createSignedPodHandshake(
                    repository = repository,
                    authManager = authManager,
                    deliveryId = deliveryId,
                    senderUserId = activeUserId,
                    recipientUserId = recipientUserId,
                    replayNonce = null,
                    tamperSignature = false,
                )
                podStatus = result.status
                if (result.accepted && result.nonce != null) {
                    lastAcceptedNonce = result.nonce
                }
            },
        ) {
            Text("Create Signed Handshake")
        }

        Button(
            onClick = {
                val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                val nonce = lastAcceptedNonce
                if (nonce <= 0L) {
                    podStatus = "NONCE_REPLAY_SKIPPED"
                    return@Button
                }

                val replayResult = createSignedPodHandshake(
                    repository = repository,
                    authManager = authManager,
                    deliveryId = deliveryId,
                    senderUserId = activeUserId,
                    recipientUserId = recipientUserId,
                    replayNonce = nonce,
                    tamperSignature = false,
                )
                podStatus = replayResult.status
            },
            enabled = lastAcceptedNonce > 0L,
        ) {
            Text("Replay Last Nonce")
        }

        Button(
            onClick = {
                val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                val tamperResult = createSignedPodHandshake(
                    repository = repository,
                    authManager = authManager,
                    deliveryId = deliveryId,
                    senderUserId = activeUserId,
                    recipientUserId = recipientUserId,
                    replayNonce = null,
                    tamperSignature = true,
                )
                podStatus = tamperResult.status
            },
        ) {
            Text("Simulate Tampered Signature")
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
                        Button(onClick = {
                            resolveConflictAction(
                                repository,
                                conflict,
                                "ACCEPT_LOCAL"
                            )
                        }) {
                            Text("Accept Local")
                        }
                        Button(onClick = {
                            resolveConflictAction(
                                repository,
                                conflict,
                                "ACCEPT_REMOTE"
                            )
                        }) {
                            Text("Accept Remote")
                        }
                        Button(onClick = {
                            resolveConflictAction(
                                repository,
                                conflict,
                                "MERGE_MANUAL"
                            )
                        }) {
                            Text("Merge Manually")
                        }
                    }
                }
            }
        }
    }
}
