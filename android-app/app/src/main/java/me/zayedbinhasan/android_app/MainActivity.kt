package me.zayedbinhasan.android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

        if (isAuthenticated) {
            AuthenticatedShell(
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
private fun AuthenticatedShell(onLogout: () -> Unit) {
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
private fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.DASHBOARD) {
            PlaceholderScreen(
                title = "Dashboard",
                description = "You can monitor overall mission progress, priority alerts, and team activity in one place.",
                statusMessage = "Status: Dashboard shell ready",
            )
        }
        composable(AppRoutes.DELIVERIES) {
            PlaceholderScreen(
                title = "Deliveries",
                description = "You can track assigned deliveries, see current state, and follow handoff progress.",
                statusMessage = "Status: Delivery screen ready for live data binding",
            )
        }
        composable(AppRoutes.ROUTE) {
            PlaceholderScreen(
                title = "Route",
                description = "You can view planned routes, changes from disruptions, and updated ETA guidance.",
                statusMessage = "Status: Route screen ready for map and reroute integration",
            )
        }
        composable(AppRoutes.POD) {
            PlaceholderScreen(
                title = "Proof of Delivery (PoD)",
                description = "You can verify handoffs with QR scan flow and delivery confirmation records.",
                statusMessage = "Status: PoD screen ready for QR workflow integration",
            )
        }
        composable(AppRoutes.SYNC_STATUS) {
            PlaceholderScreen(
                title = "Sync Status",
                description = "You can check connectivity health, sync progress, and conflict indicators before dispatch.",
                statusMessage = "Status: Sync overview ready for live sync metrics",
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, statusMessage: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = description,
                modifier = Modifier.padding(16.dp),
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Ready status",
                )
                Text(text = statusMessage)
            }
        }
    }
}