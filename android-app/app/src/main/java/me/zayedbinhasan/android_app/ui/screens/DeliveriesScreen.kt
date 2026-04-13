package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.OfflineFallbackPanel
import me.zayedbinhasan.android_app.ui.core.RbacCapability
import me.zayedbinhasan.android_app.ui.core.UiSizeClass
import me.zayedbinhasan.android_app.ui.core.allowedRolesLabel
import me.zayedbinhasan.android_app.ui.core.isRoleAllowed
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.deleteDelivery
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.insertDemoDelivery
import me.zayedbinhasan.android_app.ui.models.DeliveryFullUi

@Composable
internal fun DeliveriesScreen(
    repository: LocalRepository,
    activeRole: String,
) {
    val uiMetrics = rememberUiMetrics()
    var selectedDeliveryId by rememberSaveable { mutableStateOf<String?>(null) }
    val canCreateDelivery = isRoleAllowed(activeRole, RbacCapability.DELIVERY_CREATE)
    val canDeleteDelivery = isRoleAllowed(activeRole, RbacCapability.DELIVERY_DELETE)

    val deliveriesRaw by remember(repository) {
        repository.observeDeliveries()
    }.collectAsState(initial = emptyList())

    val pendingMutationsRaw by remember(repository) {
        repository.observePendingMutations()
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = uiMetrics.horizontalPadding, vertical = 16.dp)
            .widthIn(max = uiMetrics.contentMaxWidth),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
    ) {
        item {
            Text("Deliveries", fontWeight = FontWeight.Bold)
        }

        item {
            OfflineFallbackPanel(
                title = "Offline delivery workflow",
                guidance = "You can create and manage delivery records locally. Pending changes will sync later.",
            )
        }

        if (!canCreateDelivery || !canDeleteDelivery) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RBAC Demo", fontWeight = FontWeight.Bold)
                        Text("Current role: ${activeRole.replace('_', ' ')}")
                        Text("Create delivery: ${if (canCreateDelivery) "ALLOWED" else "DENIED"}")
                        Text("Delete delivery: ${if (canDeleteDelivery) "ALLOWED" else "DENIED"}")
                        Text("Delete roles: ${allowedRolesLabel(RbacCapability.DELIVERY_DELETE)}")
                    }
                }
            }
        }

        item {
            Button(
                onClick = { insertDemoDelivery(repository) },
                enabled = canCreateDelivery,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = uiMetrics.controlMinHeight)
                    .semantics { contentDescription = "Create demo delivery" },
            ) {
                Text("Create Delivery")
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Delivery Queue", fontWeight = FontWeight.Bold)
                    Text("Total deliveries: ${deliveries.size}")
                    Text("Pending mutations: ${pendingMutationsRaw.size}")
                }
            }
        }

        if (selected != null) {
            item {
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
        }

        items(deliveries, key = { it.taskId }) { delivery ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${delivery.taskId} • ${delivery.status}", fontWeight = FontWeight.Bold)
                    Text("${delivery.originId} -> ${delivery.destinationId}")
                    Text("Qty ${delivery.quantity}, Priority ${delivery.priority}")

                    if (uiMetrics.sizeClass == UiSizeClass.COMPACT) {
                        Button(
                            onClick = { selectedDeliveryId = delivery.taskId },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text("View Details")
                        }
                        Button(
                            onClick = { deleteDelivery(repository, delivery.taskId) },
                            enabled = canDeleteDelivery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { selectedDeliveryId = delivery.taskId },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text("Details")
                            }
                            Button(
                                onClick = { deleteDelivery(repository, delivery.taskId) },
                                enabled = canDeleteDelivery,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
