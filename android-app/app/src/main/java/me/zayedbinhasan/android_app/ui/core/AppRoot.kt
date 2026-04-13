package me.zayedbinhasan.android_app.ui.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.auth.OfflineAuthSession
import me.zayedbinhasan.android_app.data.local.db.LocalDatabaseFactory
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.screens.ConflictScreen
import me.zayedbinhasan.android_app.ui.screens.DashboardScreen
import me.zayedbinhasan.android_app.ui.screens.DeliveriesScreen
import me.zayedbinhasan.android_app.ui.screens.LoginScreen
import me.zayedbinhasan.android_app.ui.screens.PodScreen
import me.zayedbinhasan.android_app.ui.screens.ProfileScreen
import me.zayedbinhasan.android_app.ui.screens.RoutesScreen
import me.zayedbinhasan.android_app.ui.screens.SyncStatusScreen
import me.zayedbinhasan.android_app.ui.theme.AndroidappTheme
import kotlinx.coroutines.launch

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
    val navLabel: String,
    val icon: ImageVector,
)

private object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val DELIVERIES = "deliveries"
    const val ROUTE = "route"
    const val POD = "pod"
    const val SYNC_STATUS = "sync_status"
    const val CONFLICTS = "conflicts"
    const val PROFILE = "profile"
}

internal const val DEFAULT_SYNC_PEER_ID = "sync-server-main"
internal const val DEFAULT_SYNC_HTTP_BASE_URL = "http://10.0.2.2:8081"
internal const val DEFAULT_SYNC_GRPC_HOST = "10.0.2.2"
internal const val DEFAULT_SYNC_GRPC_PORT = 50051
internal const val DEFAULT_LOCAL_NODE_ID = "android_client"
internal const val DEFAULT_CHAOS_HTTP_BASE_URL = "http://10.0.2.2:5000"

private val appDestinations = listOf(
    AppDestination(route = AppRoutes.DASHBOARD, title = "Dashboard", navLabel = "Home", icon = Icons.Filled.Dashboard),
    AppDestination(route = AppRoutes.DELIVERIES, title = "Deliveries", navLabel = "Deliveries", icon = Icons.Filled.Inventory2),
    AppDestination(route = AppRoutes.ROUTE, title = "Route", navLabel = "Route", icon = Icons.Filled.Directions),
    AppDestination(route = AppRoutes.POD, title = "PoD", navLabel = "PoD", icon = Icons.Filled.QrCodeScanner),
    AppDestination(route = AppRoutes.SYNC_STATUS, title = "Sync Status", navLabel = "Sync", icon = Icons.Filled.Sync),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    session: OfflineAuthSession,
    onLogout: () -> Unit,
) {
    val uiMetrics = rememberUiMetrics()
    val navController = rememberNavController()
    val isOnline = rememberNetworkOnlineState()
    val openConflictCount by remember(repository) {
        repository.observeOpenConflictCount()
    }.collectAsState(initial = 0L)
    val pendingMutationsRaw by remember(repository) {
        repository.observePendingMutations()
    }.collectAsState(initial = emptyList())
    val receiptsRaw by remember(repository) {
        repository.observeReceipts()
    }.collectAsState(initial = emptyList())
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: appDestinations.first().route
    val currentTitle = when (currentRoute) {
        AppRoutes.CONFLICTS -> "Conflicts"
        AppRoutes.PROFILE -> "Profile"
        else -> appDestinations.firstOrNull { it.route == currentRoute }
            ?.title
            ?: "Digital Delta"
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = currentTitle,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (isOnline) {
                                "Digital Delta Operations · Online"
                            } else {
                                "Digital Delta Operations · Offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOnline) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            navController.navigate(AppRoutes.CONFLICTS) {
                                launchSingleTop = true
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReportProblem,
                            contentDescription = "Open conflicts",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            navController.navigate(AppRoutes.PROFILE) {
                                launchSingleTop = true
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Open profile",
                        )
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
                .padding(innerPadding)
                .widthIn(max = uiMetrics.contentMaxWidth)
                .padding(horizontal = uiMetrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentRoute != AppRoutes.DASHBOARD && (
                    !isOnline ||
                        pendingMutationsRaw.isNotEmpty() ||
                        openConflictCount > 0L
                    )) {
                RouteStateSnapshotRail(
                    isOnline = isOnline,
                    pendingMutationCount = pendingMutationsRaw.size,
                    openConflictCount = openConflictCount,
                    verifiedReceiptCount = receiptsRaw.count { it.verified },
                )
            }
            if (currentRoute == AppRoutes.DASHBOARD) {
                SessionStatusHeader(
                    role = session.role,
                    authMode = session.authMode,
                    isOnline = isOnline,
                    openConflictCount = openConflictCount,
                    pendingMutationCount = pendingMutationsRaw.size,
                    verifiedReceiptCount = receiptsRaw.count { it.verified },
                )
            }
            AppNavHost(
                repository = repository,
                authManager = authManager,
                navController = navController,
                session = session,
                onLogout = onLogout,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            appDestinations.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = {
                        navigateToPrimaryRoute(navController, destination.route)
                    },
                    alwaysShowLabel = true,
                    label = { Text(destination.navLabel) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.title,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
            }
        }
    }
}

private fun navigateToPrimaryRoute(
    navController: NavHostController,
    route: String,
) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    if (currentRoute == route) {
        return
    }

    // Prefer popping to an existing destination to avoid restore-state edge cases
    // when leaving non-primary routes like conflicts/profile.
    val restoredFromBackStack = navController.popBackStack(route, inclusive = false)
    if (!restoredFromBackStack) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}

@Composable
private fun SessionStatusHeader(
    role: String,
    authMode: String,
    isOnline: Boolean,
    openConflictCount: Long,
    pendingMutationCount: Int,
    verifiedReceiptCount: Int,
) {
    val uiMetrics = rememberUiMetrics()
    val incident = when {
        !isOnline -> Triple(
            "Offline Mode Active",
            "All actions are stored locally; sync is paused until validated network returns.",
            MaterialTheme.colorScheme.error,
        )
        openConflictCount > 0L -> Triple(
            "Conflict Triage Required",
            "$openConflictCount conflict(s) need operator decision before full convergence.",
            MaterialTheme.colorScheme.tertiary,
        )
        pendingMutationCount > 0 -> Triple(
            "Sync Queue In Progress",
            "$pendingMutationCount local mutation(s) are waiting to replicate.",
            MaterialTheme.colorScheme.primary,
        )
        verifiedReceiptCount > 0 -> Triple(
            "Verified Evidence Available",
            "$verifiedReceiptCount signed PoD receipt(s) are locally verifiable.",
            MaterialTheme.colorScheme.primary,
        )
        else -> Triple(
            "System Ready",
            "No active conflict or sync backlog detected.",
            MaterialTheme.colorScheme.primary,
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
        ) {
            Text("Role: ${role.replace('_', ' ')}", fontWeight = FontWeight.Bold)
            Text("Auth: ${authMode.replace('_', ' ')}")
            Text("Open conflicts: $openConflictCount")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = incident.third.copy(alpha = 0.12f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Operational Priority: ${incident.first}",
                        color = incident.third,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = incident.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    )
                }
            }
            OperationalStatusStrip(
                items = listOf(
                    StatusChipState(label = "OFFLINE", detail = if (isOnline) "ONLINE" else "OFFLINE", tone = StatusTone.OFFLINE),
                    StatusChipState(
                        label = "SYNCING",
                        detail = if (!isOnline) "WAIT_NET" else if (pendingMutationCount > 0) "QUEUED:$pendingMutationCount" else "IDLE",
                        tone = StatusTone.SYNC,
                    ),
                    StatusChipState(
                        label = "CONFLICT",
                        detail = if (openConflictCount > 0L) "OPEN:$openConflictCount" else "NONE",
                        tone = StatusTone.CONFLICT,
                    ),
                    StatusChipState(
                        label = "VERIFIED",
                        detail = if (verifiedReceiptCount > 0) "POD:$verifiedReceiptCount" else "NONE",
                        tone = StatusTone.VERIFIED,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun RouteStateSnapshotRail(
    isOnline: Boolean,
    pendingMutationCount: Int,
    openConflictCount: Long,
    verifiedReceiptCount: Int,
) {
    val title = when {
        !isOnline -> "Offline mode active"
        openConflictCount > 0L -> "Conflict attention needed"
        pendingMutationCount > 0 -> "Sync queue pending"
        else -> "System active"
    }
    val detail = "Net ${if (isOnline) "ONLINE" else "OFFLINE"} · Sync ${if (!isOnline) "WAIT_NET" else if (pendingMutationCount > 0) "QUEUED:$pendingMutationCount" else "IDLE"} · Conflict ${if (openConflictCount > 0L) "OPEN:$openConflictCount" else "NONE"} · Verified ${if (verifiedReceiptCount > 0) "POD:$verifiedReceiptCount" else "NONE"}"
    val toneColor = when {
        !isOnline -> MaterialTheme.colorScheme.error
        openConflictCount > 0L -> MaterialTheme.colorScheme.tertiary
        pendingMutationCount > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = toneColor.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = toneColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun rememberNetworkOnlineState(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var isOnline by remember { mutableStateOf(connectivityManager.isCurrentlyOnline()) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                coroutineScope.launch {
                    isOnline = true
                }
            }

            override fun onLost(network: Network) {
                coroutineScope.launch {
                    isOnline = connectivityManager.isCurrentlyOnline()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                coroutineScope.launch {
                    isOnline = validated
                }
            }

            override fun onUnavailable() {
                coroutineScope.launch {
                    isOnline = false
                }
            }
        }

        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }

        onDispose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }

    return isOnline
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val active = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(active) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun AppNavHost(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    navController: NavHostController,
    session: OfflineAuthSession,
    onLogout: () -> Unit,
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
            DeliveriesScreen(
                repository = repository,
                activeRole = session.role,
            )
        }
        composable(AppRoutes.ROUTE) {
            RoutesScreen(
                repository = repository,
                activeRole = session.role,
                onGoDashboard = {
                    navigateToPrimaryRoute(navController, AppRoutes.DASHBOARD)
                },
            )
        }
        composable(AppRoutes.POD) {
            PodScreen(
                repository = repository,
                authManager = authManager,
                activeUserId = session.userId,
            )
        }
        composable(AppRoutes.SYNC_STATUS) {
            SyncStatusScreen(
                repository = repository,
                activeRole = session.role,
            )
        }
        composable(AppRoutes.CONFLICTS) {
            ConflictScreen(
                repository = repository,
                activeRole = session.role,
                onGoDashboard = {
                    navigateToPrimaryRoute(navController, AppRoutes.DASHBOARD)
                },
                onGoRoute = {
                    navigateToPrimaryRoute(navController, AppRoutes.ROUTE)
                },
            )
        }
        composable(AppRoutes.PROFILE) {
            ProfileScreen(
                session = session,
                onLogout = onLogout,
            )
        }
    }
}
