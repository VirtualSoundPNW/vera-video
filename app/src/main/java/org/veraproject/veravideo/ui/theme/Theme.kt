package org.veraproject.veravideo.ui.theme

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

// The Vera Project's identity is a bright, high-contrast red against near-black.
private val VeraRed = Color(0xFFE03127)
private val VeraRedDark = Color(0xFFB3241C)
private val VeraRedLight = Color(0xFFFF6B5E)
private val Ink = Color(0xFF141414)

private val LightColors = lightColorScheme(
    primary = VeraRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Ink,
    onSecondary = Color.White,
    surface = Color(0xFFFFFBFF),
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = VeraRedLight,
    onPrimary = Color(0xFF690002),
    primaryContainer = VeraRedDark,
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7E0E0),
    onSecondary = Ink,
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFE7E0E0),
)

@Composable
fun VeraVideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You on Android 12+; the brand palette is the fallback.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeraTypography,
        content = content,
    )
}
