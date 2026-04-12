package me.zayedbinhasan.android_app.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import me.zayedbinhasan.android_app.ui.screens.ConflictScreen
import me.zayedbinhasan.android_app.ui.screens.DashboardScreen
import me.zayedbinhasan.android_app.ui.screens.DeliveriesScreen
import me.zayedbinhasan.android_app.ui.screens.LoginScreen
import me.zayedbinhasan.android_app.ui.screens.PodScreen
import me.zayedbinhasan.android_app.ui.screens.RoutesScreen
import me.zayedbinhasan.android_app.ui.screens.SyncStatusScreen
import me.zayedbinhasan.android_app.ui.theme.AndroidappTheme

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
internal const val DEFAULT_CHAOS_HTTP_BASE_URL = "http://10.0.2.2:5000"

private val appDestinations = listOf(
    AppDestination(route = AppRoutes.DASHBOARD, title = "Dashboard", icon = Icons.Filled.Dashboard),
    AppDestination(route = AppRoutes.DELIVERIES, title = "Deliveries", icon = Icons.Filled.Inventory2),
    AppDestination(route = AppRoutes.ROUTE, title = "Route", icon = Icons.Filled.Directions),
    AppDestination(route = AppRoutes.POD, title = "PoD", icon = Icons.Filled.QrCodeScanner),
    AppDestination(route = AppRoutes.SYNC_STATUS, title = "Sync Status", icon = Icons.Filled.Sync),
)

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
