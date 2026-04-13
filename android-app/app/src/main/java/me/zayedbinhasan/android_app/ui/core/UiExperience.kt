package me.zayedbinhasan.android_app.ui.core

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
internal fun screenBackgroundBrush(): Brush {
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    return remember(background, accent) {
        Brush.verticalGradient(
            colors = listOf(
                background,
                accent,
                background,
            ),
        )
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
internal fun ScreenHeroHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "$title logo",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
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
