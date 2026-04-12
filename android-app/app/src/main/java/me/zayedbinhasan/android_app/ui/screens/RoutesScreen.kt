package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.canManageRouteActions
import me.zayedbinhasan.android_app.ui.logic.deleteRoute
import me.zayedbinhasan.android_app.ui.logic.insertDemoRoute
import me.zayedbinhasan.android_app.ui.models.RouteFullUi

@Composable
internal fun RoutesScreen(
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
