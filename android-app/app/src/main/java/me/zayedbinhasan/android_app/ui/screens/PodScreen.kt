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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.createSignedPodHandshake
import me.zayedbinhasan.android_app.ui.logic.deleteReceipt
import me.zayedbinhasan.android_app.ui.models.ReceiptFullUi

@Composable
internal fun PodScreen(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    activeUserId: String,
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Proof of Delivery", fontWeight = FontWeight.Bold)
        Text("Sender: $activeUserId -> Recipient: $recipientUserId")
        Text("PoD State: $podStatus")

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
        ) {
            Text("Create Signed Handshake")
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
        ) {
            Text("Replay Last Nonce")
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
        ) {
            Text("Simulate Tampered Signature")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(receipts, key = { it.receiptId }) { receipt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${receipt.receiptId} • Delivery ${receipt.deliveryId}", fontWeight = FontWeight.Bold)
                        Text("Sender: ${receipt.senderUserId} -> Recipient: ${receipt.recipientUserId}")
                        Text("Nonce: ${receipt.nonce}")
                        Text(if (receipt.verified) "Verification: Accepted" else "Verification: Pending")
                        Button(onClick = { deleteReceipt(repository, receipt.receiptId) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
