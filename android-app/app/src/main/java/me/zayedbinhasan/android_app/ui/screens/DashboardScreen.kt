package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import me.zayedbinhasan.android_app.ui.logic.deleteUser
import me.zayedbinhasan.android_app.ui.logic.insertDemoUser
import me.zayedbinhasan.android_app.ui.models.DeliveryUi
import me.zayedbinhasan.android_app.ui.models.ReceiptUi
import me.zayedbinhasan.android_app.ui.models.RouteUi
import me.zayedbinhasan.android_app.ui.models.UserUi

@Composable
internal fun DashboardScreen(repository: LocalRepository) {
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
