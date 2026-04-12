package me.zayedbinhasan.android_app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
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

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

private const val OTP_DIGITS = 6
private const val OTP_PERIOD_SECONDS = 30L

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
        prefs.edit()
            .remove(KEY_SESSION_USER_ID)
            .remove(KEY_SESSION_ROLE)
            .apply()
    }

    fun provisionIdentity(userId: String, role: String): CachedIdentity? {
        return runCatching {
        val normalizedUserId = userId.trim().ifEmpty { "field-volunteer" }
        val normalizedRole = role.trim().ifEmpty { "FIELD_VOLUNTEER" }
        val alias = "digital_delta_key_$normalizedUserId"
        val publicKey = DeviceKeyStore.ensureKeyAndGetPublicKey(alias)
            ?: return null
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
            prefs.edit().putString(KEY_CACHED_SECRET, Base64.encodeToString(secret, Base64.NO_WRAP)).apply()
        }

        prefs.edit()
            .putString(KEY_CACHED_USER_ID, normalizedUserId)
            .putString(KEY_CACHED_ROLE, normalizedRole)
            .putString(KEY_CACHED_ALIAS, alias)
            .apply()

        return CachedIdentity(
            userId = normalizedUserId,
            role = normalizedRole,
            keyAlias = alias,
            publicKey = publicKey,
        )
        }.getOrNull()
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
        return runCatching {
        val identity = cachedIdentity() ?: return null
        val targetUserId = userId.trim().ifEmpty { identity.userId }
        if (identity.userId != targetUserId) {
            return null
        }

        if (!DeviceKeyStore.hasKey(identity.keyAlias)) {
            return null
        }

        if (!verifyOtp(otp.trim())) {
            return null
        }

        val user = repository.userById(identity.userId) ?: return null
        if (!user.active) {
            return null
        }

        prefs.edit()
            .putString(KEY_SESSION_USER_ID, user.user_id)
            .putString(KEY_SESSION_ROLE, user.role)
            .apply()

        appendMutation(entityType = "auth_session", entityId = user.user_id, operationType = "UPSERT_OFFLINE_SESSION")

        return OfflineAuthSession(
            userId = user.user_id,
            role = user.role,
            authMode = "OFFLINE_RELOGIN",
        )
        }.getOrNull()
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

    private fun appendMutation(entityType: String, entityId: String, operationType: String) {
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
                actorId = "offline_auth",
                deviceId = deviceId,
                mutationTimestamp = System.currentTimeMillis(),
                synced = false,
            )
        }
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

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
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
