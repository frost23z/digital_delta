package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.auth.OfflineAuthManager
import me.zayedbinhasan.android_app.auth.OfflineAuthSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginScreen(
    authManager: OfflineAuthManager,
    onLoginSuccess: (OfflineAuthSession) -> Unit,
) {
    var identifierInput by rememberSaveable { mutableStateOf("") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var cachedIdentityUserId by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.userId ?: "") }
    var cachedIdentityRole by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.role ?: "") }
    var cachedIdentityPublicKey by rememberSaveable { mutableStateOf(authManager.cachedIdentity()?.publicKey ?: "") }
    var otpPreview by rememberSaveable { mutableStateOf(authManager.generateCurrentOtp()?.code ?: "") }
    var otpExpiresIn by rememberSaveable { mutableLongStateOf(authManager.generateCurrentOtp()?.expiresInSeconds ?: 0L) }
    var offlineAuthState by rememberSaveable {
        mutableStateOf(if (cachedIdentityUserId.isNotEmpty()) "IDENTITY_CACHED" else "NO_IDENTITY")
    }
    var sessionStatus by rememberSaveable {
        mutableStateOf(if (authManager.restoreActiveSession() != null) "SESSION_AVAILABLE" else "SIGNED_OUT")
    }

    LaunchedEffect(Unit) {
        val cached = authManager.cachedIdentity()
        cachedIdentityUserId = cached?.userId ?: ""
        cachedIdentityRole = cached?.role ?: ""
        cachedIdentityPublicKey = cached?.publicKey ?: ""

        val otp = authManager.generateCurrentOtp()
        otpPreview = otp?.code ?: ""
        otpExpiresIn = otp?.expiresInSeconds ?: 0L

        offlineAuthState = if (cached != null) "IDENTITY_CACHED" else "NO_IDENTITY"
        sessionStatus = if (authManager.restoreActiveSession() != null) "SESSION_AVAILABLE" else "SIGNED_OUT"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Digital Delta Login") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Welcome to Digital Delta", fontWeight = FontWeight.Bold)
                    Text("Provision once, then re-login offline with local OTP and cached role.")
                    Text("Offline Auth State: $offlineAuthState")
                    Text("Session Status: $sessionStatus")
                    if (cachedIdentityRole.isNotEmpty()) {
                        Text("Role Badge: $cachedIdentityRole", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = identifierInput,
                onValueChange = { identifierInput = it },
                label = { Text("User ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = otpInput,
                onValueChange = { otpInput = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("TOTP (6 digits)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (otpPreview.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Local OTP Preview: $otpPreview")
                        Text("Expires In: ${otpExpiresIn}s")
                        if (cachedIdentityPublicKey.isNotEmpty()) {
                            Text("Device Public Key (cached): ${cachedIdentityPublicKey.take(24)}...")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val userId = identifierInput.ifEmpty { cachedIdentityUserId.ifEmpty { "field-volunteer" } }
                    val identity = authManager.provisionIdentity(
                        userId = userId,
                        role = cachedIdentityRole.ifEmpty { "FIELD_VOLUNTEER" },
                    )
                    if (identity != null) {
                        cachedIdentityUserId = identity.userId
                        cachedIdentityRole = identity.role
                        cachedIdentityPublicKey = identity.publicKey
                        identifierInput = identity.userId
                        val otp = authManager.generateCurrentOtp()
                        otpPreview = otp?.code ?: ""
                        otpExpiresIn = otp?.expiresInSeconds ?: 0L
                        offlineAuthState = "OTP_REQUIRED"
                        sessionStatus = "PROVISIONED"
                    } else {
                        offlineAuthState = "PROVISION_FAILED"
                        sessionStatus = "KEYSTORE_OR_STORAGE_ERROR"
                    }
                },
            ) {
                Text("Provision Identity (Offline Ready)")
            }

            Button(
                onClick = {
                    val otp = authManager.generateCurrentOtp()
                    otpPreview = otp?.code ?: ""
                    otpExpiresIn = otp?.expiresInSeconds ?: 0L
                    if (otp != null) {
                        offlineAuthState = "OTP_REQUIRED"
                        sessionStatus = "OTP_REFRESHED"
                    } else {
                        offlineAuthState = "NO_IDENTITY"
                        sessionStatus = "OTP_NOT_AVAILABLE"
                    }
                },
                enabled = cachedIdentityUserId.isNotEmpty(),
            ) {
                Text("Regenerate Local OTP")
            }

            Button(
                onClick = {
                    if (otpPreview.length == 6) {
                        otpInput = otpPreview
                        sessionStatus = "OTP_APPLIED"
                    }
                },
                enabled = otpPreview.length == 6,
            ) {
                Text("Use OTP Preview")
            }

            Button(
                onClick = {
                    val candidateOtp = otpInput.ifEmpty { otpPreview }
                    val session = authManager.loginOffline(
                        userId = identifierInput.ifEmpty { cachedIdentityUserId },
                        otp = candidateOtp,
                    )
                    if (session != null) {
                        onLoginSuccess(session)
                    } else {
                        val lockoutRemaining = authManager.lockoutRemainingSeconds()
                        if (lockoutRemaining > 0L) {
                            offlineAuthState = "AUTH_LOCKED"
                            sessionStatus = "LOCKED_${lockoutRemaining}s"
                        } else {
                            offlineAuthState = "AUTH_FAILED"
                            sessionStatus = "OTP_OR_IDENTITY_INVALID"
                        }
                    }
                },
                enabled = cachedIdentityUserId.isNotEmpty() && (otpInput.length == 6 || otpPreview.length == 6),
            ) {
                Text("Login Offline")
            }

            Button(
                onClick = {
                    val session = authManager.restoreActiveSession()
                    if (session != null) {
                        onLoginSuccess(session)
                    } else {
                        offlineAuthState = "NO_ACTIVE_SESSION"
                        sessionStatus = "NO_ACTIVE_SESSION"
                    }
                },
                enabled = authManager.restoreActiveSession() != null,
            ) {
                Text("Continue Cached Session")
            }
        }
    }
}

