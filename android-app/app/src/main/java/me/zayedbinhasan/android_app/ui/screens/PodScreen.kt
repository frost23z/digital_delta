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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.core.OfflineFallbackPanel
import me.zayedbinhasan.android_app.ui.core.OperationalStatusStrip
import me.zayedbinhasan.android_app.ui.core.StatusChipState
import me.zayedbinhasan.android_app.ui.core.StatusTone
import me.zayedbinhasan.android_app.ui.core.UiSizeClass
import me.zayedbinhasan.android_app.ui.logic.m5_pod.counterSignPendingReceipt
import me.zayedbinhasan.android_app.ui.logic.m5_pod.createSignedPodHandshake
import me.zayedbinhasan.android_app.ui.logic.m5_pod.deleteReceipt
import me.zayedbinhasan.android_app.ui.models.ReceiptFullUi
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics

@Composable
internal fun PodScreen(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    activeUserId: String,
) {
    val uiMetrics = rememberUiMetrics()
    val receiptsRaw by remember(repository) {
        repository.observeReceipts()
    }.collectAsState(initial = emptyList())
    var podStatus by rememberSaveable { mutableStateOf("Idle") }
    var lastAcceptedNonce by rememberSaveable { mutableLongStateOf(0L) }
    val recipientUserId = "recipient-02"

    val receipts = receiptsRaw.map { row ->
        ReceiptFullUi(
            receiptId = row.receipt_id,
            deliveryId = row.delivery_id,
            senderUserId = row.sender_user_id,
            recipientUserId = row.recipient_user_id,
            nonce = row.nonce,
            verified = row.verified,
            verifiedAt = row.verified_at,
        )
    }
    val pendingCountersignCount = receipts.count { !it.verified }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = uiMetrics.horizontalPadding, vertical = 16.dp)
            .widthIn(max = uiMetrics.contentMaxWidth),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
    ) {
        item {
            Text("Proof of Delivery", fontWeight = FontWeight.Bold)
        }

        item {
            OperationalStatusStrip(
                items = listOf(
                    StatusChipState(label = "OFFLINE", detail = "READY", tone = StatusTone.OFFLINE),
                    StatusChipState(label = "SYNCING", detail = "IDLE", tone = StatusTone.SYNC),
                    StatusChipState(label = "CONFLICT", detail = "NONE", tone = StatusTone.CONFLICT),
                    StatusChipState(
                        label = "VERIFIED",
                        detail = if (receipts.any { it.verified }) "POD_OK" else "PENDING",
                        tone = StatusTone.VERIFIED,
                    ),
                ),
            )
        }

        item {
            OfflineFallbackPanel(
                title = "Offline PoD behavior",
                guidance = "Sender sign, recipient countersign, and replay/tamper checks run locally. If sync is unavailable, receipts stay local and sync later.",
            )
        }

        item {
            Text("Sender: $activeUserId -> Recipient: $recipientUserId")
        }

        item {
            Text("PoD State: $podStatus")
        }

        item {
            Text("Pending recipient countersign: $pendingCountersignCount")
        }

        item {
            Button(
                onClick = {
                    val senderRole = repository.userById(activeUserId)?.role ?: "FIELD_VOLUNTEER"
                    val repairedIdentity = authManager.provisionIdentity(activeUserId, senderRole)
                    podStatus = if (repairedIdentity != null) {
                        "SIGNING_IDENTITY_REPAIRED"
                    } else {
                        "SIGNING_IDENTITY_REPAIR_FAILED"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = uiMetrics.controlMinHeight)
                    .semantics { contentDescription = "Repair sender signing identity and public key mapping" },
            ) {
                Text("Repair Sender Key")
            }
        }

        if (uiMetrics.sizeClass == UiSizeClass.COMPACT) {
            item {
                Button(
                    onClick = {
                        val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                        val result = createSignedPodHandshake(
                            repository = repository,
                            authManager = authManager,
                            deliveryId = deliveryId,
                            senderUserId = activeUserId,
                            recipientUserId = recipientUserId,
                            replayNonce = null,
                            tamperSignature = false,
                        )
                        podStatus = result.status
                        if (result.accepted && result.nonce != null) {
                            lastAcceptedNonce = result.nonce
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = uiMetrics.controlMinHeight)
                        .semantics { contentDescription = "Create signed proof of delivery handshake" },
                ) {
                    Text("Create Signed Handshake")
                }
            }

            item {
                Button(
                    onClick = {
                        val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                        val nonce = lastAcceptedNonce
                        if (nonce <= 0L) {
                            podStatus = "NONCE_REPLAY_SKIPPED"
                            return@Button
                        }

                        val replayResult = createSignedPodHandshake(
                            repository = repository,
                            authManager = authManager,
                            deliveryId = deliveryId,
                            senderUserId = activeUserId,
                            recipientUserId = recipientUserId,
                            replayNonce = nonce,
                            tamperSignature = false,
                        )
                        podStatus = replayResult.status
                    },
                    enabled = lastAcceptedNonce > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = uiMetrics.controlMinHeight)
                        .semantics { contentDescription = "Replay last nonce to validate anti-replay protection" },
                ) {
                    Text("Replay Last Nonce")
                }
            }

            item {
                Button(
                    onClick = {
                        val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                        val tamperResult = createSignedPodHandshake(
                            repository = repository,
                            authManager = authManager,
                            deliveryId = deliveryId,
                            senderUserId = activeUserId,
                            recipientUserId = recipientUserId,
                            replayNonce = null,
                            tamperSignature = true,
                        )
                        podStatus = tamperResult.status
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = uiMetrics.controlMinHeight)
                        .semantics { contentDescription = "Simulate tampered signature validation failure" },
                ) {
                    Text("Tamper Test (Should Fail)")
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                            val result = createSignedPodHandshake(
                                repository = repository,
                                authManager = authManager,
                                deliveryId = deliveryId,
                                senderUserId = activeUserId,
                                recipientUserId = recipientUserId,
                                replayNonce = null,
                                tamperSignature = false,
                            )
                            podStatus = result.status
                            if (result.accepted && result.nonce != null) {
                                lastAcceptedNonce = result.nonce
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = uiMetrics.controlMinHeight),
                    ) {
                        Text("Create")
                    }
                    Button(
                        onClick = {
                            val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                            val nonce = lastAcceptedNonce
                            if (nonce <= 0L) {
                                podStatus = "NONCE_REPLAY_SKIPPED"
                                return@Button
                            }

                            val replayResult = createSignedPodHandshake(
                                repository = repository,
                                authManager = authManager,
                                deliveryId = deliveryId,
                                senderUserId = activeUserId,
                                recipientUserId = recipientUserId,
                                replayNonce = nonce,
                                tamperSignature = false,
                            )
                            podStatus = replayResult.status
                        },
                        enabled = lastAcceptedNonce > 0L,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = uiMetrics.controlMinHeight),
                    ) {
                        Text("Replay")
                    }
                    Button(
                        onClick = {
                            val deliveryId = repository.firstDeliveryOrNull()?.task_id ?: "task-demo"
                            val tamperResult = createSignedPodHandshake(
                                repository = repository,
                                authManager = authManager,
                                deliveryId = deliveryId,
                                senderUserId = activeUserId,
                                recipientUserId = recipientUserId,
                                replayNonce = null,
                                tamperSignature = true,
                            )
                            podStatus = tamperResult.status
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = uiMetrics.controlMinHeight),
                    ) {
                        Text("Tamper Test")
                    }
                }
            }
        }

        items(receipts, key = { it.receiptId }) { receipt ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${receipt.receiptId} • Delivery ${receipt.deliveryId}", fontWeight = FontWeight.Bold)
                    Text("Sender: ${receipt.senderUserId} -> Recipient: ${receipt.recipientUserId}")
                    Text("Nonce: ${receipt.nonce}")
                    Text(if (receipt.verified) "Verification: Sender + Recipient Verified" else "Verification: Pending Recipient Countersign")
                    if (!receipt.verified) {
                        Button(
                            onClick = {
                                val countersignResult = counterSignPendingReceipt(
                                    repository = repository,
                                    authManager = authManager,
                                    receiptId = receipt.receiptId,
                                )
                                podStatus = countersignResult.status
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight)
                                .semantics { contentDescription = "Countersign pending proof of delivery receipt ${receipt.receiptId}" },
                        ) {
                            Text("Countersign Pending Receipt")
                        }
                    }
                    Button(
                        onClick = { deleteReceipt(repository, receipt.receiptId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = uiMetrics.controlMinHeight),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}
