package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.auth.OfflineAuthSession
import me.zayedbinhasan.android_app.ui.core.UiSizeClass
import me.zayedbinhasan.android_app.ui.core.allRbacCapabilitySpecs
import me.zayedbinhasan.android_app.ui.core.isRoleAllowed
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics

@Composable
internal fun ProfileScreen(
    session: OfflineAuthSession,
    onLogout: () -> Unit,
) {
    val uiMetrics = rememberUiMetrics()
    var previewRole by rememberSaveable { mutableStateOf(session.role) }
    val previewRoles = listOf("FIELD_VOLUNTEER", "SUPPLY_MANAGER", "CAMP_COMMANDER", "SYNC_ADMIN")
    val roleLabel = session.role.replace('_', ' ')
    val authLabel = session.authMode.replace('_', ' ')
    val roleEmoji = when {
        session.role.contains("COMMANDER") -> "🧭"
        session.role.contains("DRONE") -> "🚁"
        session.role.contains("MANAGER") -> "📋"
        session.role.contains("VOLUNTEER") -> "🤝"
        else -> "👤"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = uiMetrics.horizontalPadding, vertical = 16.dp)
            .widthIn(max = uiMetrics.contentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(uiMetrics.sectionSpacing),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$roleEmoji Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Current operator session and account controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("User Info", fontWeight = FontWeight.Bold)
                Text("User ID: ${session.userId}")
                Text("Role: $roleLabel")
                Text("Auth Mode: $authLabel")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Quick Notes ✨", fontWeight = FontWeight.Bold)
                Text("Use this panel to verify who is signed in before sync, routing, and triage operations.")
                Text("For safety, keep role-restricted actions with authorized accounts only.")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("RBAC Demo Matrix", fontWeight = FontWeight.Bold)
                Text("Policy-backed capability map for judge walkthrough and operator training.")
                Text("Preview Role: ${previewRole.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)

                if (uiMetrics.sizeClass == UiSizeClass.COMPACT) {
                    previewRoles.forEach { role ->
                        Button(
                            onClick = { previewRole = role },
                            enabled = previewRole != role,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = uiMetrics.controlMinHeight),
                        ) {
                            Text(role.replace('_', ' '))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        previewRoles.take(2).forEach { role ->
                            Button(
                                onClick = { previewRole = role },
                                enabled = previewRole != role,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text(role.replace('_', ' '))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        previewRoles.drop(2).forEach { role ->
                            Button(
                                onClick = { previewRole = role },
                                enabled = previewRole != role,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = uiMetrics.controlMinHeight),
                            ) {
                                Text(role.replace('_', ' '))
                            }
                        }
                    }
                }
            }
        }

        allRbacCapabilitySpecs().forEach { spec ->
            val granted = isRoleAllowed(previewRole, spec.capability)
            val tone = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.1f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(spec.title, fontWeight = FontWeight.Bold, color = tone)
                    Text(spec.description)
                    Text("Access: ${if (granted) "ALLOWED" else "DENIED"}", fontWeight = FontWeight.SemiBold)
                    Text("Allowed roles: ${spec.allowedRoles.joinToString(", ")}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Live Enforcement Wired", fontWeight = FontWeight.Bold)
                Text("Route create/delete/recompute checks use this same policy.")
                Text("Conflict resolution actions are disabled for non-manager roles.")
                Text("Server sync trigger and delivery delete are role-gated from this matrix.")
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = uiMetrics.controlMinHeight),
        ) {
            Text("Logout")
        }
    }
}
