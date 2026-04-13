package me.zayedbinhasan.android_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.auth.OfflineAuthSession
import me.zayedbinhasan.android_app.ui.core.rememberUiMetrics

@Composable
internal fun ProfileScreen(
    session: OfflineAuthSession,
    onLogout: () -> Unit,
) {
    val uiMetrics = rememberUiMetrics()
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
