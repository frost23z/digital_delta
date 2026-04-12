package me.zayedbinhasan.android_app.ui.logic.m5_pod

import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

internal data class PodHandshakeResult(
    val accepted: Boolean,
    val status: String,
    val nonce: Long?,
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

    val senderSignature = if (tamperSignature) {
        tamperBase64Signature(generatedSignature)
    } else {
        generatedSignature
    }

    val senderPublicKey = repository.userById(senderUserId)?.public_key
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

    val verified = authManager.verifyPayloadHashSignature(
        publicKeyBase64 = senderPublicKey,
        payloadHash = payloadHash,
        signatureBase64 = senderSignature,
    )
    if (!verified) {
        appendMutation(
            repository = repository,
            entityType = "receipt",
            entityId = "verify-$nonce",
            operationType = "POD_SIGNATURE_VERIFICATION_FAILED",
            changedFieldsJson = JSONObject().apply {
                put("nonce", nonce)
                put("delivery_id", deliveryId)
                put("tampered", tamperSignature)
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

    val receiptId = "receipt-${UUID.randomUUID().toString().take(8)}"
    repository.upsertReceipt(
        receiptId = receiptId,
        deliveryId = deliveryId,
        senderUserId = senderUserId,
        recipientUserId = recipientUserId,
        payloadHash = payloadHash,
        nonce = nonce,
        senderSignature = senderSignature,
        recipientSignature = "RECIPIENT_ACK_PENDING",
        verified = true,
        verifiedAt = timestamp,
    )

    val mutationDetails = JSONObject().apply {
        put("delivery_id", deliveryId)
        put("sender_user_id", senderUserId)
        put("recipient_user_id", recipientUserId)
        put("nonce", nonce)
        put("timestamp", timestamp)
        put("verified", true)
    }.toString()

    appendMutation(
        repository = repository,
        entityType = "receipt",
        entityId = receiptId,
        operationType = "POD_HANDSHAKE_VERIFIED",
        changedFieldsJson = mutationDetails,
        actorId = senderUserId,
    )
    authManager.recordAuditEvent(
        eventType = "POD_HANDSHAKE_VERIFIED",
        actorId = senderUserId,
        entityType = "receipt",
        entityId = receiptId,
    )

    return PodHandshakeResult(
        accepted = true,
        status = "HANDSHAKE_ACCEPTED",
        nonce = nonce,
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
