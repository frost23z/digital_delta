package me.zayedbinhasan.android_app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import me.zayedbinhasan.android_app.data.local.repository.LocalRepository

private const val AUTH_PREFS = "digital_delta_offline_auth"
private const val KEY_CACHED_USER_ID = "cached_user_id"
private const val KEY_CACHED_ROLE = "cached_role"
private const val KEY_CACHED_ALIAS = "cached_alias"
private const val KEY_CACHED_SECRET = "cached_totp_secret"
private const val KEY_SESSION_USER_ID = "session_user_id"
private const val KEY_SESSION_ROLE = "session_role"
private const val KEY_FAILED_OTP_ATTEMPTS = "failed_otp_attempts"
private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

private const val OTP_DIGITS = 6
private const val OTP_PERIOD_SECONDS = 30L
private const val OTP_MAX_FAILED_ATTEMPTS = 5
private const val OTP_LOCKOUT_DURATION_MS = 2 * 60 * 1000L
//noinspection SpellCheckingInspection
private const val OTP_HMAC_ALGORITHM = "HmacSHA256"

data class CachedIdentity(
    val userId: String,
    val role: String,
    val keyAlias: String,
    val publicKey: String,
)

data class OfflineAuthSession(
    val userId: String,
    val role: String,
    val authMode: String,
)

data class OtpPreview(
    val code: String,
    val expiresInSeconds: Long,
)

class OfflineAuthManager(
    context: Context,
    private val repository: LocalRepository,
) {
    private val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)

    fun cachedIdentity(): CachedIdentity? {
        val userId = prefs.getString(KEY_CACHED_USER_ID, null) ?: return null
        val role = prefs.getString(KEY_CACHED_ROLE, null) ?: return null
        val keyAlias = prefs.getString(KEY_CACHED_ALIAS, null) ?: return null
        val publicKey = DeviceKeyStore.ensureKeyAndGetPublicKey(keyAlias) ?: return null
        return CachedIdentity(
            userId = userId,
            role = role,
            keyAlias = keyAlias,
            publicKey = publicKey,
        )
    }

    fun restoreActiveSession(): OfflineAuthSession? {
        val userId = prefs.getString(KEY_SESSION_USER_ID, null) ?: return null
        val role = prefs.getString(KEY_SESSION_ROLE, null) ?: return null
        val identity = cachedIdentity() ?: return null
        if (identity.userId != userId || identity.role != role) {
            clearActiveSession()
            return null
        }
        val user = repository.userById(userId) ?: return null
        if (!user.active) {
            clearActiveSession()
            return null
        }
        return OfflineAuthSession(user.user_id, user.role, authMode = "SESSION_RESUME")
    }

    fun clearActiveSession() {
        prefs.edit {
            remove(KEY_SESSION_USER_ID)
            remove(KEY_SESSION_ROLE)
        }
    }

    fun provisionIdentity(userId: String, role: String): CachedIdentity? {
        val normalizedUserId = userId.trim().ifEmpty { "field-volunteer" }
        val normalizedRole = role.trim().ifEmpty { "FIELD_VOLUNTEER" }

        return try {
            val alias = "digital_delta_key_$normalizedUserId"
            val publicKey = DeviceKeyStore.ensureKeyAndGetPublicKey(alias)
            if (publicKey == null) {
                appendAuditEvent(
                    eventType = "AUTH_PROVISION_FAILURE",
                    actorId = normalizedUserId,
                    entityType = "user",
                    entityId = normalizedUserId,
                )
                return null
            }
            val now = System.currentTimeMillis()

            val existing = repository.userById(normalizedUserId)
            val createdAt = existing?.created_at ?: now

            repository.upsertUser(
                userId = normalizedUserId,
                displayName = normalizedUserId,
                role = normalizedRole,
                publicKey = publicKey,
                active = true,
                createdAt = createdAt,
                updatedAt = now,
            )
            appendMutation(entityType = "user", entityId = normalizedUserId, operationType = "UPSERT_AUTH_PROVISION")

            if (readSecret() == null) {
                val secret = ByteArray(20)
                SecureRandom().nextBytes(secret)
                prefs.edit {
                    putString(KEY_CACHED_SECRET, Base64.encodeToString(secret, Base64.NO_WRAP))
                }
            }

            prefs.edit {
                putString(KEY_CACHED_USER_ID, normalizedUserId)
                putString(KEY_CACHED_ROLE, normalizedRole)
                putString(KEY_CACHED_ALIAS, alias)
            }

            appendAuditEvent(
                eventType = "AUTH_PROVISION_SUCCESS",
                actorId = normalizedUserId,
                entityType = "user",
                entityId = normalizedUserId,
            )

            CachedIdentity(
                userId = normalizedUserId,
                role = normalizedRole,
                keyAlias = alias,
                publicKey = publicKey,
            )
        } catch (_: Exception) {
            appendAuditEvent(
                eventType = "AUTH_PROVISION_FAILURE",
                actorId = normalizedUserId,
                entityType = "user",
                entityId = normalizedUserId,
            )
            null
        }
    }

    fun generateCurrentOtp(): OtpPreview? {
        return runCatching {
            val secret = readSecret() ?: return null
            val nowSeconds = System.currentTimeMillis() / 1000L
            val code = Totp.generate(secret, nowSeconds)
            val expiresIn = OTP_PERIOD_SECONDS - (nowSeconds % OTP_PERIOD_SECONDS)
            OtpPreview(code = code, expiresInSeconds = expiresIn)
        }.getOrNull()
    }

    fun loginOffline(userId: String, otp: String): OfflineAuthSession? {
        val identity = cachedIdentity()
        val targetUserId = userId.trim().ifEmpty { identity?.userId ?: "" }
        if (targetUserId.isBlank()) {
            appendAuditEvent(
                eventType = "AUTH_LOGIN_FAILURE",
                actorId = "unknown",
                entityType = "auth_session",
                entityId = "unknown",
            )
            return null
        }

        if (currentLockoutRemainingSeconds() > 0L) {
            appendAuditEvent(
                eventType = "AUTH_LOGIN_LOCKED",
                actorId = targetUserId,
                entityType = "auth_session",
                entityId = targetUserId,
            )
            return null
        }

        if (identity == null || identity.userId != targetUserId) {
            appendAuditEvent(
                eventType = "AUTH_LOGIN_FAILURE",
                actorId = targetUserId,
                entityType = "auth_session",
                entityId = targetUserId,
            )
            return null
        }

        if (!DeviceKeyStore.hasKey(identity.keyAlias)) {
            appendAuditEvent(
                eventType = "AUTH_LOGIN_FAILURE",
                actorId = targetUserId,
                entityType = "auth_session",
                entityId = targetUserId,
            )
            return null
        }

        if (!verifyOtp(otp.trim())) {
            registerFailedOtpAttempt(targetUserId)
            return null
        }

        val user = repository.userById(identity.userId)
        if (user == null || !user.active) {
            appendAuditEvent(
                eventType = "AUTH_LOGIN_FAILURE",
                actorId = targetUserId,
                entityType = "auth_session",
                entityId = targetUserId,
            )
            return null
        }

        prefs.edit {
            putString(KEY_SESSION_USER_ID, user.user_id)
            putString(KEY_SESSION_ROLE, user.role)
        }

        clearFailedOtpState()
        appendMutation(entityType = "auth_session", entityId = user.user_id, operationType = "UPSERT_OFFLINE_SESSION")
        appendAuditEvent(
            eventType = "AUTH_LOGIN_SUCCESS",
            actorId = user.user_id,
            entityType = "auth_session",
            entityId = user.user_id,
        )

        return OfflineAuthSession(
            userId = user.user_id,
            role = user.role,
            authMode = "OFFLINE_RELOGIN",
        )
    }

    fun lockoutRemainingSeconds(): Long = currentLockoutRemainingSeconds()

    fun signPayloadHashForUser(userId: String, payloadHash: String): String? {
        val alias = keyAliasForUser(userId)
        DeviceKeyStore.ensureKeyAndGetPublicKey(alias) ?: return null
        return DeviceKeyStore.signPayloadHash(alias, payloadHash)
    }

    fun ensureUserPublicKey(userId: String): String? {
        val alias = keyAliasForUser(userId)
        return DeviceKeyStore.ensureKeyAndGetPublicKey(alias)
    }

    fun verifyPayloadHashSignature(publicKeyBase64: String, payloadHash: String, signatureBase64: String): Boolean {
        return DeviceKeyStore.verifyPayloadHashSignature(
            publicKeyBase64 = publicKeyBase64,
            payloadHash = payloadHash,
            signatureBase64 = signatureBase64,
        )
    }

    fun verifyPayloadHashSignatureForUser(userId: String, payloadHash: String, signatureBase64: String): Boolean {
        val alias = keyAliasForUser(userId)
        DeviceKeyStore.ensureKeyAndGetPublicKey(alias) ?: return false
        return DeviceKeyStore.verifyPayloadHashSignatureByAlias(
            alias = alias,
            payloadHash = payloadHash,
            signatureBase64 = signatureBase64,
        )
    }

    fun recordAuditEvent(
        eventType: String,
        actorId: String,
        entityType: String,
        entityId: String,
    ) {
        appendAuditEvent(
            eventType = eventType,
            actorId = actorId,
            entityType = entityType,
            entityId = entityId,
        )
    }

    private fun registerFailedOtpAttempt(userId: String) {
        val nextAttempts = prefs.getInt(KEY_FAILED_OTP_ATTEMPTS, 0) + 1

        appendAuditEvent(
            eventType = "AUTH_LOGIN_FAILURE",
            actorId = userId,
            entityType = "auth_session",
            entityId = userId,
        )

        if (nextAttempts >= OTP_MAX_FAILED_ATTEMPTS) {
            prefs.edit {
                putInt(KEY_FAILED_OTP_ATTEMPTS, 0)
                putLong(KEY_LOCKOUT_UNTIL_MS, System.currentTimeMillis() + OTP_LOCKOUT_DURATION_MS)
            }
            appendAuditEvent(
                eventType = "AUTH_LOGIN_LOCKED",
                actorId = userId,
                entityType = "auth_session",
                entityId = userId,
            )
        } else {
            prefs.edit {
                putInt(KEY_FAILED_OTP_ATTEMPTS, nextAttempts)
            }
        }
    }

    private fun clearFailedOtpState() {
        prefs.edit {
            putInt(KEY_FAILED_OTP_ATTEMPTS, 0)
            remove(KEY_LOCKOUT_UNTIL_MS)
        }
    }

    private fun currentLockoutRemainingSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (lockoutUntil <= 0L) {
            return 0L
        }

        val remainingMillis = lockoutUntil - System.currentTimeMillis()
        return if (remainingMillis <= 0L) {
            0L
        } else {
            (remainingMillis + 999L) / 1000L
        }
    }

    private fun verifyOtp(otp: String): Boolean {
        if (otp.length != OTP_DIGITS) {
            return false
        }
        val secret = readSecret() ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000L

        val validNow = Totp.generate(secret, nowSeconds)
        if (otp == validNow) {
            return true
        }

        val validPrev = Totp.generate(secret, nowSeconds - OTP_PERIOD_SECONDS)
        if (otp == validPrev) {
            return true
        }

        val validNext = Totp.generate(secret, nowSeconds + OTP_PERIOD_SECONDS)
        return otp == validNext
    }

    private fun readSecret(): ByteArray? {
        val secretBase64 = prefs.getString(KEY_CACHED_SECRET, null) ?: return null
        return runCatching {
            Base64.decode(secretBase64, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun keyAliasForUser(userId: String): String {
        val normalizedUserId = userId.trim()
        val cachedUserId = prefs.getString(KEY_CACHED_USER_ID, null)
        val cachedAlias = prefs.getString(KEY_CACHED_ALIAS, null)
        if (cachedUserId == normalizedUserId && !cachedAlias.isNullOrBlank()) {
            return cachedAlias
        }
        return "digital_delta_key_$normalizedUserId"
    }

    private fun appendMutation(
        entityType: String,
        entityId: String,
        operationType: String,
        actorId: String = "offline_auth",
    ) {
        runCatching {
            val deviceId = "android_client"
            val nextCounter = repository.localDeviceMutationCount(deviceId) + 1
            val vectorClockJson = "{\"$deviceId\":$nextCounter}"
            repository.insertMutation(
                mutationId = "mut-auth-${System.currentTimeMillis()}",
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                changedFieldsJson = "{}",
                vectorClockJson = vectorClockJson,
                actorId = actorId,
                deviceId = deviceId,
                mutationTimestamp = System.currentTimeMillis(),
                synced = false,
            )
        }
    }

    private fun appendAuditEvent(
        eventType: String,
        actorId: String,
        entityType: String,
        entityId: String,
    ) {
        runCatching {
            val timestamp = System.currentTimeMillis()
            val prevHash = repository.latestAuditHash() ?: "GENESIS"
            val canonicalEventPayload = listOf(
                eventType,
                actorId,
                entityType,
                entityId,
                timestamp.toString(),
            ).joinToString("|")
            val eventHash = sha256Hex("$prevHash|$canonicalEventPayload")

            repository.insertAuditEvent(
                eventId = "audit-${UUID.randomUUID()}",
                eventType = eventType,
                actorId = actorId,
                entityType = entityType,
                entityId = entityId,
                eventTimestamp = timestamp,
                prevHash = prevHash,
                eventHash = eventHash,
            )
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

private object DeviceKeyStore {
    fun hasKey(alias: String): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.containsAlias(alias)
        }.getOrDefault(false)
    }

    fun ensureKeyAndGetPublicKey(alias: String): String? {
        if (!hasKey(alias)) {
            generate(alias)
        }
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val certificate = keyStore.getCertificate(alias) ?: return null
            Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
        }.getOrNull()
    }

    fun signPayloadHash(alias: String, payloadHash: String): String? {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return null

            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(payloadHash.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun verifyPayloadHashSignature(publicKeyBase64: String, payloadHash: String, signatureBase64: String): Boolean {
        return runCatching {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(payloadHash.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        }.getOrDefault(false)
    }

    fun verifyPayloadHashSignatureByAlias(alias: String, payloadHash: String, signatureBase64: String): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val certificate = keyStore.getCertificate(alias) ?: return false
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(certificate)
            verifier.update(payloadHash.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        }.getOrDefault(false)
    }

    private fun generate(alias: String) {
        runCatching {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(false)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
    }
}

private object Totp {
    fun generate(secret: ByteArray, timeSeconds: Long): String {
        val counter = timeSeconds / OTP_PERIOD_SECONDS
        val data = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance(OTP_HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, OTP_HMAC_ALGORITHM))
        val hash = mac.doFinal(data)

        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val otp = binary % 1_000_000
        return otp.toString().padStart(OTP_DIGITS, '0')
    }
}
