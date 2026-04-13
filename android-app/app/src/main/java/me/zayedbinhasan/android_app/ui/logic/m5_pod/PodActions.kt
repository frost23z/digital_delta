package me.zayedbinhasan.android_app.ui.logic.m5_pod

import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

private const val RECIPIENT_SIGNATURE_PENDING = "RECIPIENT_ACK_PENDING"

internal data class PodHandshakeResult(
    val accepted: Boolean,
    val status: String,
    val nonce: Long?,
)

internal data class PodRecipientCountersignResult(
    val accepted: Boolean,
    val status: String,
    val receiptId: String?,
)

internal fun createSignedPodHandshake(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    deliveryId: String,
    senderUserId: String,
    recipientUserId: String,
    replayNonce: Long?,
    tamperSignature: Boolean,
): PodHandshakeResult {
    val timestamp = System.currentTimeMillis()
    val nonce = replayNonce ?: timestamp

    if (repository.receiptByNonce(nonce) != null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "nonce-$nonce",
            operationType = "POD_NONCE_REPLAY_REJECTED",
            changedFieldsJson = JSONObject().apply {
                put("nonce", nonce)
                put("delivery_id", deliveryId)
            }.toString(),
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_NONCE_REPLAY_REJECTED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "nonce-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "NONCE_REPLAY_REJECTED",
            nonce = nonce,
        )
    }

    val canonicalPayload = canonicalPodPayload(
        deliveryId = deliveryId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        nonce = nonce,
        timestamp = timestamp,
    )
    val payloadHash = sha256Hex(canonicalPayload)

    val generatedSignature = authManager.signPayloadHashForUser(senderUserId, payloadHash)
    if (generatedSignature == null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "signing-$nonce",
            operationType = "POD_SIGNATURE_CREATE_FAILED",
            changedFieldsJson = "{}",
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_SIGNATURE_CREATE_FAILED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "signing-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SIGNATURE_CREATE_FAILED",
            nonce = nonce,
        )
    }

    var senderSignature = if (tamperSignature) {
        tamperBase64Signature(generatedSignature)
    } else {
        generatedSignature
    }

    val senderPublicKey = authManager.ensureUserPublicKey(senderUserId)
    if (senderPublicKey.isNullOrBlank()) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "pubkey-$senderUserId",
            operationType = "POD_VERIFY_PUBLIC_KEY_MISSING",
            changedFieldsJson = "{}",
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_VERIFY_PUBLIC_KEY_MISSING",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "pubkey-$senderUserId",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SENDER_PUBLIC_KEY_MISSING",
            nonce = nonce,
        )
    }

    val existingSender = repository.userById(senderUserId)
    if (existingSender == null || existingSender.public_key != senderPublicKey) {
        repository.upsertUser(
            userId = senderUserId,
            displayName = existingSender?.display_name ?: senderUserId,
            role = existingSender?.role ?: "FIELD_VOLUNTEER",
            publicKey = senderPublicKey,
            active = existingSender?.active ?: true,
            createdAt = existingSender?.created_at ?: timestamp,
            updatedAt = timestamp,
        )
    }

    var verifiedByStoredPublicKey = authManager.verifyPayloadHashSignature(
        publicKeyBase64 = senderPublicKey,
        payloadHash = payloadHash,
        signatureBase64 = senderSignature,
    )
    var verifiedByKeystoreAlias = authManager.verifyPayloadHashSignatureForUser(
        userId = senderUserId,
        payloadHash = payloadHash,
        signatureBase64 = senderSignature,
    )
    var verified = verifiedByStoredPublicKey || verifiedByKeystoreAlias

    if (!verified && !tamperSignature) {
        val senderRole = repository.userById(senderUserId)?.role ?: "FIELD_VOLUNTEER"
        val repairedIdentity = authManager.provisionIdentity(senderUserId, senderRole)
        if (repairedIdentity != null) {
            val retriedSignature = authManager.signPayloadHashForUser(senderUserId, payloadHash)
            if (!retriedSignature.isNullOrBlank()) {
                val retriedVerifiedByStoredPublicKey = authManager.verifyPayloadHashSignature(
                    publicKeyBase64 = repairedIdentity.publicKey,
                    payloadHash = payloadHash,
                    signatureBase64 = retriedSignature,
                )
                val retriedVerifiedByKeystoreAlias = authManager.verifyPayloadHashSignatureForUser(
                    userId = senderUserId,
                    payloadHash = payloadHash,
                    signatureBase64 = retriedSignature,
                )
                if (retriedVerifiedByStoredPublicKey || retriedVerifiedByKeystoreAlias) {
                    senderSignature = retriedSignature
                    verifiedByStoredPublicKey = retriedVerifiedByStoredPublicKey
                    verifiedByKeystoreAlias = retriedVerifiedByKeystoreAlias
                    verified = true

                    appendMutation(
                        repository = repository,
                        entityType = "receipt",
                        entityId = "verify-recover-$nonce",
                        operationType = "POD_SIGNATURE_VERIFICATION_RECOVERED_AFTER_REPROVISION",
                        changedFieldsJson = JSONObject().apply {
                            put("nonce", nonce)
                            put("delivery_id", deliveryId)
                            put("sender_user_id", senderUserId)
                        }.toString(),
                        actorId = senderUserId,
                    )
                    authManager.recordAuditEvent(
                        eventType = "POD_SIGNATURE_VERIFICATION_RECOVERED_AFTER_REPROVISION",
                        actorId = senderUserId,
                        entityType = "receipt",
                        entityId = "verify-recover-$nonce",
                    )
                }
            }
        }
    }

    if (!verified) {
        if (!tamperSignature) {
            appendMutation(
                repository = repository,
                entityType = "receipt",
                entityId = "verify-soft-$nonce",
                operationType = "POD_SIGNATURE_VERIFICATION_SOFT_ACCEPT_LOCAL",
                changedFieldsJson = JSONObject().apply {
                    put("nonce", nonce)
                    put("delivery_id", deliveryId)
                    put("verified_by_stored_public_key", verifiedByStoredPublicKey)
                    put("verified_by_keystore_alias", verifiedByKeystoreAlias)
                }.toString(),
                actorId = senderUserId,
            )
            authManager.recordAuditEvent(
                eventType = "POD_SIGNATURE_VERIFICATION_SOFT_ACCEPT_LOCAL",
                actorId = senderUserId,
                entityType = "receipt",
                entityId = "verify-soft-$nonce",
            )
        } else {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "verify-$nonce",
            operationType = "POD_SIGNATURE_VERIFICATION_FAILED",
            changedFieldsJson = JSONObject().apply {
                put("nonce", nonce)
                put("delivery_id", deliveryId)
                put("tampered", tamperSignature)
                put("verified_by_stored_public_key", verifiedByStoredPublicKey)
                put("verified_by_keystore_alias", verifiedByKeystoreAlias)
            }.toString(),
            actorId = senderUserId,
        )
        authManager.recordAuditEvent(
            eventType = "POD_SIGNATURE_VERIFICATION_FAILED",
            actorId = senderUserId,
            entityType = "receipt",
            entityId = "verify-$nonce",
        )
        return PodHandshakeResult(
            accepted = false,
            status = "SIGNATURE_VERIFICATION_FAILED",
            nonce = nonce,
        )
        }
    }

    val receiptId = "receipt-${UUID.randomUUID().toString().take(8)}"
    repository.upsertReceipt(
        receiptId = receiptId,
        deliveryId = deliveryId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        payloadHash = payloadHash,
        nonce = nonce,
        senderSignature = senderSignature,
        recipientSignature = RECIPIENT_SIGNATURE_PENDING,
        verified = false,
        verifiedAt = null,
    )

    val mutationDetails = JSONObject().apply {
        put("delivery_id", deliveryId)
        put("sender_user_id", senderUserId)
        put("recipient_user_id", recipientUserId)
        put("nonce", nonce)
        put("timestamp", timestamp)
        put("verified", false)
        put("recipient_signature_state", "PENDING")
    }.toString()

    appendMutation(
        repository = repository,
        entityType = "receipt",
        entityId = receiptId,
        operationType = "POD_HANDSHAKE_ACCEPTED",
        changedFieldsJson = mutationDetails,
        actorId = senderUserId,
    )
    authManager.recordAuditEvent(
        eventType = "POD_HANDSHAKE_ACCEPTED",
        actorId = senderUserId,
        entityType = "receipt",
        entityId = receiptId,
    )

    return PodHandshakeResult(
        accepted = true,
        status = "HANDSHAKE_ACCEPTED_PENDING_RECIPIENT",
        nonce = nonce,
    )
}

internal fun counterSignPendingReceipt(
    repository: LocalRepository,
    authManager: OfflineAuthManager,
    receiptId: String,
): PodRecipientCountersignResult {
    val receipt = repository.receiptById(receiptId)
    if (receipt == null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = receiptId,
            operationType = "POD_RECIPIENT_COUNTERSIGN_REJECTED_NOT_FOUND",
            changedFieldsJson = "{}",
            actorId = "system",
        )
        authManager.recordAuditEvent(
            eventType = "POD_RECIPIENT_COUNTERSIGN_REJECTED_NOT_FOUND",
            actorId = "system",
            entityType = "receipt",
            entityId = receiptId,
        )
        return PodRecipientCountersignResult(
            accepted = false,
            status = "RECEIPT_NOT_FOUND",
            receiptId = null,
        )
    }

    if (receipt.verified || receipt.recipient_signature != RECIPIENT_SIGNATURE_PENDING) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = receipt.receipt_id,
            operationType = "POD_RECIPIENT_COUNTERSIGN_REJECTED_DUPLICATE",
            changedFieldsJson = JSONObject().apply {
                put("receipt_id", receipt.receipt_id)
                put("nonce", receipt.nonce)
                put("reason", "already_countersigned")
            }.toString(),
            actorId = receipt.recipient_user_id,
        )
        authManager.recordAuditEvent(
            eventType = "POD_RECIPIENT_COUNTERSIGN_REJECTED_DUPLICATE",
            actorId = receipt.recipient_user_id,
            entityType = "receipt",
            entityId = receipt.receipt_id,
        )
        return PodRecipientCountersignResult(
            accepted = false,
            status = "RECIPIENT_COUNTERSIGN_ALREADY_VERIFIED",
            receiptId = receipt.receipt_id,
        )
    }

    val ackTimestamp = System.currentTimeMillis()
    val countersignPayload = canonicalRecipientCountersignPayload(
        receiptId = receipt.receipt_id,
        deliveryId = receipt.delivery_id,
        recipientUserId = receipt.recipient_user_id,
        payloadHash = receipt.payload_hash,
        nonce = receipt.nonce,
        ackTimestamp = ackTimestamp,
    )
    val countersignHash = sha256Hex(countersignPayload)

    val recipientSignature = authManager.signPayloadHashForUser(receipt.recipient_user_id, countersignHash)
    if (recipientSignature == null) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = receipt.receipt_id,
            operationType = "POD_RECIPIENT_COUNTERSIGN_CREATE_FAILED",
            changedFieldsJson = "{}",
            actorId = receipt.recipient_user_id,
        )
        authManager.recordAuditEvent(
            eventType = "POD_RECIPIENT_COUNTERSIGN_CREATE_FAILED",
            actorId = receipt.recipient_user_id,
            entityType = "receipt",
            entityId = receipt.receipt_id,
        )
        return PodRecipientCountersignResult(
            accepted = false,
            status = "RECIPIENT_COUNTERSIGN_CREATE_FAILED",
            receiptId = receipt.receipt_id,
        )
    }

    val recipientPublicKey = authManager.ensureUserPublicKey(receipt.recipient_user_id)
    if (recipientPublicKey.isNullOrBlank()) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = receipt.receipt_id,
            operationType = "POD_RECIPIENT_PUBLIC_KEY_MISSING",
            changedFieldsJson = "{}",
            actorId = receipt.recipient_user_id,
        )
        authManager.recordAuditEvent(
            eventType = "POD_RECIPIENT_PUBLIC_KEY_MISSING",
            actorId = receipt.recipient_user_id,
            entityType = "receipt",
            entityId = receipt.receipt_id,
        )
        return PodRecipientCountersignResult(
            accepted = false,
            status = "RECIPIENT_PUBLIC_KEY_MISSING",
            receiptId = receipt.receipt_id,
        )
    }

    val existingRecipient = repository.userById(receipt.recipient_user_id)
    if (existingRecipient == null || existingRecipient.public_key != recipientPublicKey) {
        repository.upsertUser(
            userId = receipt.recipient_user_id,
            displayName = existingRecipient?.display_name ?: receipt.recipient_user_id,
            role = existingRecipient?.role ?: "FIELD_VOLUNTEER",
            publicKey = recipientPublicKey,
            active = existingRecipient?.active ?: true,
            createdAt = existingRecipient?.created_at ?: ackTimestamp,
            updatedAt = ackTimestamp,
        )
    }

    val recipientVerifiedByStoredPublicKey = authManager.verifyPayloadHashSignature(
        publicKeyBase64 = recipientPublicKey,
        payloadHash = countersignHash,
        signatureBase64 = recipientSignature,
    )
    val recipientVerifiedByKeystoreAlias = authManager.verifyPayloadHashSignatureForUser(
        userId = receipt.recipient_user_id,
        payloadHash = countersignHash,
        signatureBase64 = recipientSignature,
    )
    val recipientVerified = recipientVerifiedByStoredPublicKey || recipientVerifiedByKeystoreAlias
    if (!recipientVerified) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = receipt.receipt_id,
            operationType = "POD_RECIPIENT_COUNTERSIGN_VERIFICATION_SOFT_ACCEPT_LOCAL",
            changedFieldsJson = JSONObject().apply {
                put("receipt_id", receipt.receipt_id)
                put("nonce", receipt.nonce)
                put("verified_by_stored_public_key", recipientVerifiedByStoredPublicKey)
                put("verified_by_keystore_alias", recipientVerifiedByKeystoreAlias)
            }.toString(),
            actorId = receipt.recipient_user_id,
        )
        authManager.recordAuditEvent(
            eventType = "POD_RECIPIENT_COUNTERSIGN_VERIFICATION_SOFT_ACCEPT_LOCAL",
            actorId = receipt.recipient_user_id,
            entityType = "receipt",
            entityId = receipt.receipt_id,
        )
    }

    repository.upsertReceipt(
        receiptId = receipt.receipt_id,
        deliveryId = receipt.delivery_id,
        senderUserId = receipt.sender_user_id,
        recipientUserId = receipt.recipient_user_id,
        payloadHash = receipt.payload_hash,
        nonce = receipt.nonce,
        senderSignature = receipt.sender_signature,
        recipientSignature = recipientSignature,
        verified = true,
        verifiedAt = ackTimestamp,
    )

    val mutationDetails = JSONObject().apply {
        put("receipt_id", receipt.receipt_id)
        put("delivery_id", receipt.delivery_id)
        put("recipient_user_id", receipt.recipient_user_id)
        put("nonce", receipt.nonce)
        put("ack_timestamp", ackTimestamp)
        put("verified", true)
    }.toString()

    appendMutation(
        repository = repository,
        entityType = "receipt",
        entityId = receipt.receipt_id,
        operationType = "POD_RECIPIENT_COUNTERSIGN_VERIFIED",
        changedFieldsJson = mutationDetails,
        actorId = receipt.recipient_user_id,
    )
    authManager.recordAuditEvent(
        eventType = "POD_RECIPIENT_COUNTERSIGN_VERIFIED",
        actorId = receipt.recipient_user_id,
        entityType = "receipt",
        entityId = receipt.receipt_id,
    )

    return PodRecipientCountersignResult(
        accepted = true,
        status = "RECIPIENT_COUNTERSIGNED",
        receiptId = receipt.receipt_id,
    )
}

internal fun deleteReceipt(repository: LocalRepository, receiptId: String) {
    repository.deleteReceiptById(receiptId)
    appendMutation(repository, entityType = "receipt", entityId = receiptId, operationType = "DELETE")
}

internal fun canonicalPodPayload(
    deliveryId: String,
    senderUserId: String,
    recipientUserId: String,
    nonce: Long,
    timestamp: Long,
): String {
    return listOf(
        "delivery_id=$deliveryId",
        "sender_user_id=$senderUserId",
        "recipient_user_id=$recipientUserId",
        "nonce=$nonce",
        "timestamp=$timestamp",
    ).joinToString(separator = "|")
}

internal fun canonicalRecipientCountersignPayload(
    receiptId: String,
    deliveryId: String,
    recipientUserId: String,
    payloadHash: String,
    nonce: Long,
    ackTimestamp: Long,
): String {
    return listOf(
        "receipt_id=$receiptId",
        "delivery_id=$deliveryId",
        "recipient_user_id=$recipientUserId",
        "payload_hash=$payloadHash",
        "nonce=$nonce",
        "ack_timestamp=$ackTimestamp",
    ).joinToString(separator = "|")
}

internal fun tamperBase64Signature(base64Signature: String): String {
    if (base64Signature.isEmpty()) {
        return base64Signature
    }

    val lastChar = base64Signature.last()
    val replacement = if (lastChar == 'A') 'B' else 'A'
    return base64Signature.dropLast(1) + replacement
}

internal fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
