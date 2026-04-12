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
import me.zayedbinhasan.android_app.ui.logic.resolveConflictAction
import me.zayedbinhasan.android_app.ui.logic.simulateRemoteOwnershipConflict
import me.zayedbinhasan.android_app.ui.logic.simulateRemoteQuantityMerge
import me.zayedbinhasan.android_app.ui.logic.simulateRemoteStatusMerge
import me.zayedbinhasan.android_app.ui.models.ConflictUi

@Composable
internal fun ConflictScreen(repository: LocalRepository) {
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
