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
import me.zayedbinhasan.android_app.ui.core.UiSizeClass
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.resolveConflictAction
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.simulateRemoteOwnershipConflict
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.simulateRemoteQuantityMerge
import me.zayedbinhasan.android_app.ui.logic.m2_crdt.simulateRemoteStatusMerge
import me.zayedbinhasan.android_app.ui.models.ConflictUi
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics

@Composable
internal fun ConflictScreen(
    repository: LocalRepository,
    onGoDashboard: (() -> Unit)? = null,
    onGoRoute: (() -> Unit)? = null,
) {
    val uiMetrics = rememberUiMetrics()
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = uiMetrics.horizontalPadding, vertical = 16.dp)
            .widthIn(max = uiMetrics.contentMaxWidth),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
    ) {
        item {
            Text("Conflict Resolution", fontWeight = FontWeight.Bold)
        }

        item {
            OperationalStatusStrip(
                items = listOf(
                    StatusChipState(label = "OFFLINE", detail = "READY", tone = StatusTone.OFFLINE),
                    StatusChipState(label = "SYNCING", detail = "IDLE", tone = StatusTone.SYNC),
                    StatusChipState(
                        label = "CONFLICT",
                        detail = if (conflicts.isNotEmpty()) "OPEN:${conflicts.size}" else "NONE",
                        tone = StatusTone.CONFLICT,
                    ),
                    StatusChipState(label = "VERIFIED", detail = "RULES", tone = StatusTone.INFO),
                ),
            )
        }

        item {
            OfflineFallbackPanel(
                title = "Offline conflict handling",
                guidance = "Conflict review and resolution are local-first. Resolve now and sync resolution events when network is available.",
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open Conflicts: ${conflicts.size}")
                    Text("Merge rules: LWW for scalars, additive for quantities, manual for ownership")

                    if (onGoDashboard != null || onGoRoute != null) {
                        if (uiMetrics.sizeClass == UiSizeClass.COMPACT) {
                            onGoDashboard?.let { navigateToDashboard ->
                                Button(
                                    onClick = navigateToDashboard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = uiMetrics.controlMinHeight),
                                ) {
                                    Text("Go Dashboard")
                                }
                            }
                            onGoRoute?.let { navigateToRoute ->
                                Button(
                                    onClick = navigateToRoute,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = uiMetrics.controlMinHeight),
                                ) {
                                    Text("Go Route")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                onGoDashboard?.let { navigateToDashboard ->
                                    Button(
                                        onClick = navigateToDashboard,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = uiMetrics.controlMinHeight),
                                    ) {
                                        Text("Dashboard")
                                    }
                                }
                                onGoRoute?.let { navigateToRoute ->
                                    Button(
                                        onClick = navigateToRoute,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = uiMetrics.controlMinHeight),
                                    ) {
                                        Text("Route")
                                    }
                                }
                            }
                        }
                    }

                    if (uiMetrics.sizeClass == UiSizeClass.COMPACT) {
                        Button(
                            onClick = { simulateRemoteStatusMerge(repository) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text("Simulate LWW Status Merge")
                        }
                        Button(
                            onClick = { simulateRemoteQuantityMerge(repository) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text("Simulate Additive Quantity Merge")
                        }
                        Button(
                            onClick = { simulateRemoteOwnershipConflict(repository) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text("Simulate Ownership Conflict")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { simulateRemoteStatusMerge(repository) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text("LWW")
                            }
                            Button(
                                onClick = { simulateRemoteQuantityMerge(repository) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text("Additive")
                            }
                            Button(
                                onClick = { simulateRemoteOwnershipConflict(repository) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text("Ownership")
                            }
                        }
                    }
                }
            }
        }

        items(conflicts, key = { it.conflictId }) { conflict ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${conflict.entityType}:${conflict.entityId}", fontWeight = FontWeight.Bold)
                    Text("Field: ${conflict.fieldName}")
                    Text("Strategy: ${conflict.mergeStrategy}")
                    Text("Local: ${conflict.localValue ?: "(null)"}")
                    Text("Remote: ${conflict.remoteValue ?: "(null)"}")
                    Text(if (conflict.manualRequired) "Badge: CONFLICT_DETECTED" else "Badge: AUTO_MERGED")
                    Button(
                        onClick = { resolveConflictAction(repository, conflict, "ACCEPT_LOCAL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = uiMetrics.controlMinHeight)
                            .semantics { contentDescription = "Accept local value for ${conflict.conflictId}" },
                    ) {
                        Text("Accept Local")
                    }
                    Button(
                        onClick = { resolveConflictAction(repository, conflict, "ACCEPT_REMOTE") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = uiMetrics.controlMinHeight)
                            .semantics { contentDescription = "Accept remote value for ${conflict.conflictId}" },
                    ) {
                        Text("Accept Remote")
                    }
                    Button(
                        onClick = { resolveConflictAction(repository, conflict, "MERGE_MANUAL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = uiMetrics.controlMinHeight)
                            .semantics { contentDescription = "Manually merge conflict ${conflict.conflictId}" },
                    ) {
                        Text("Merge Manually")
                    }
                }
            }
        }
    }
}
