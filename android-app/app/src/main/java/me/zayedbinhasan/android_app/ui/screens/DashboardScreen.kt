package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.OfflineFallbackPanel
import me.zayedbinhasan.android_app.ui.core.OperationalStatusStrip
import me.zayedbinhasan.android_app.ui.core.StatusChipState
import me.zayedbinhasan.android_app.ui.core.StatusTone
import me.zayedbinhasan.android_app.ui.logic.m1_auth.deleteUser
import me.zayedbinhasan.android_app.ui.logic.m1_auth.insertDemoUser
import me.zayedbinhasan.android_app.ui.models.DeliveryUi
import me.zayedbinhasan.android_app.ui.models.ReceiptUi
import me.zayedbinhasan.android_app.ui.models.RouteUi
import me.zayedbinhasan.android_app.ui.models.UserUi
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics

@Composable
internal fun DashboardScreen(repository: LocalRepository) {
    val uiMetrics = rememberUiMetrics()
    val usersRaw by remember(repository) {
        repository.observeUsers()
    }.collectAsState(initial = emptyList())

    val pendingMutationsRaw by remember(repository) {
        repository.observePendingMutations()
    }.collectAsState(initial = emptyList())

    val openConflictCount by remember(repository) {
        repository.observeOpenConflictCount()
    }.collectAsState(initial = 0L)

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
            .padding(horizontal = uiMetrics.horizontalPadding, vertical = 16.dp)
            .widthIn(max = uiMetrics.contentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
    ) {
        Text("Mission Overview", fontWeight = FontWeight.Bold)

        OperationalStatusStrip(
            items = listOf(
                StatusChipState(label = "OFFLINE", detail = "READY", tone = StatusTone.OFFLINE),
                StatusChipState(
                    label = "SYNCING",
                    detail = if (pendingMutationsRaw.isNotEmpty()) "QUEUED:${pendingMutationsRaw.size}" else "IDLE",
                    tone = StatusTone.SYNC,
                ),
                StatusChipState(
                    label = "CONFLICT",
                    detail = if (openConflictCount > 0L) "OPEN:$openConflictCount" else "NONE",
                    tone = StatusTone.CONFLICT,
                ),
                StatusChipState(
                    label = "VERIFIED",
                    detail = if (receipts.count { it.verified } > 0) "POD:${receipts.count { it.verified }}" else "NONE",
                    tone = StatusTone.VERIFIED,
                ),
            ),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiMetrics.sizeClass == me.zayedbinhasan.android_app.ui.core.UiSizeClass.COMPACT) {
                    Text("Profiles: ${users.size}")
                    Text("Deliveries: ${deliveries.size}")
                    Text("Routes: ${routes.size}")
                    Text("PoD Records: ${receipts.size}")
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Profiles: ${users.size}")
                            Text("Deliveries: ${deliveries.size}")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Routes: ${routes.size}")
                            Text("PoD Records: ${receipts.size}")
                        }
                    }
                }
            }
        }

        OfflineFallbackPanel(
            title = "Offline fallback",
            guidance = "Core local flows remain usable without network. Queue changes and run Sync Status once connectivity returns.",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Team Profile", fontWeight = FontWeight.Bold)
                val user = users.firstOrNull()
                if (user == null) {
                    Text("No local profile yet. Add a profile to enable offline role-based actions.")
                    Button(
                        onClick = { insertDemoUser(repository) },
                        modifier = Modifier
                            .heightIn(min = uiMetrics.controlMinHeight)
                            .semantics { contentDescription = "Add demo profile" },
                    ) {
                        Text("Add Demo Profile")
                    }
                } else {
                    Text("${user.displayName} (${user.role})")
                    Text(if (user.active) "Status: Active" else "Status: Inactive")
                    Button(
                        onClick = { deleteUser(repository, user.userId) },
                        modifier = Modifier
                            .heightIn(min = uiMetrics.controlMinHeight)
                            .semantics { contentDescription = "Delete current profile" },
                    ) {
                        Text("Delete Profile")
                    }
                }
            }
        }
    }
}
