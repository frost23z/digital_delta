package me.zayedbinhasan.android_app.ui.core

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.zayedbinhasan.android_app.ui.theme.StatusCritical
import me.zayedbinhasan.android_app.ui.theme.StatusInfo
import me.zayedbinhasan.android_app.ui.theme.StatusOffline
import me.zayedbinhasan.android_app.ui.theme.StatusVerified
import me.zayedbinhasan.android_app.ui.theme.StatusWarning

internal enum class UiSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class UiMetrics(
    val sizeClass: UiSizeClass,
    val horizontalPadding: Dp,
    val sectionSpacing: Dp,
    val contentMaxWidth: Dp,
    val controlMinHeight: Dp,
)

@Composable
internal fun rememberUiMetrics(): UiMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp >= 1440 -> UiMetrics(
                sizeClass = UiSizeClass.EXPANDED,
                horizontalPadding = 28.dp,
                sectionSpacing = 16.dp,
                contentMaxWidth = 1240.dp,
                controlMinHeight = 52.dp,
            )
            widthDp >= 768 -> UiMetrics(
                sizeClass = UiSizeClass.MEDIUM,
                horizontalPadding = 20.dp,
                sectionSpacing = 14.dp,
                contentMaxWidth = 960.dp,
                controlMinHeight = 50.dp,
            )
            else -> UiMetrics(
                sizeClass = UiSizeClass.COMPACT,
                horizontalPadding = 14.dp,
                sectionSpacing = 12.dp,
                contentMaxWidth = 680.dp,
                controlMinHeight = 48.dp,
            )
        }
    }
}

internal enum class StatusTone {
    OFFLINE,
    SYNC,
    CONFLICT,
    VERIFIED,
    INFO,
}

internal data class StatusChipState(
    val label: String,
    val detail: String,
    val tone: StatusTone,
)

@Composable
internal fun OperationalStatusStrip(
    items: List<StatusChipState>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics {
                contentDescription = "Operational status strip"
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val (containerColor, contentColor) = statusColors(item.tone)
            Card(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics {
                        contentDescription = "${item.label}: ${item.detail}"
                    },
                colors = CardDefaults.cardColors(
                    containerColor = containerColor.copy(alpha = 0.18f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.label,
                        color = containerColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.detail,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OfflineFallbackPanel(
    title: String,
    guidance: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Offline fallback panel" },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = guidance,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun statusColors(tone: StatusTone): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (tone) {
        StatusTone.OFFLINE -> StatusOffline to MaterialTheme.colorScheme.onSurface
        StatusTone.SYNC -> StatusWarning to MaterialTheme.colorScheme.onSurface
        StatusTone.CONFLICT -> StatusCritical to MaterialTheme.colorScheme.onSurface
        StatusTone.VERIFIED -> StatusVerified to MaterialTheme.colorScheme.onSurface
        StatusTone.INFO -> StatusInfo to MaterialTheme.colorScheme.onSurface
    }
}
