package me.zayedbinhasan.android_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF03263D),
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF0B2333),
    tertiary = Pink80,
    onTertiary = Color(0xFF351E00),
    background = Color(0xFF0E1622),
    onBackground = Color(0xFFE7EEF5),
    surface = Color(0xFF162130),
    onSurface = Color(0xFFE7EEF5),
    error = StatusCritical,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    tertiary = Pink40,
    onTertiary = Color.White,
    background = Color(0xFFF2F7FB),
    onBackground = Color(0xFF102033),
    surface = Color.White,
    onSurface = Color(0xFF102033),
    error = StatusCritical,
    onError = Color.White,
)

@Composable
fun AndroidappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}