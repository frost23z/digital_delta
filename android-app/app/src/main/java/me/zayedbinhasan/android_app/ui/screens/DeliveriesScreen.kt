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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.deleteDelivery
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.insertDemoDelivery
import me.zayedbinhasan.android_app.ui.models.DeliveryFullUi

@Composable
internal fun DeliveriesScreen(repository: LocalRepository) {
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
