package com.digitalclockpro.core.ui.theme

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
import com.digitalclockpro.domain.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = LedAmber,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = TextDim
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF006877),
    secondary = NeonPurple,
    tertiary = Color(0xFFB26A00)
)

/**
 * Material You theme engine: dynamic colour on Android 12+, an AMOLED pure-black variant, and a
 * user-selectable accent that overrides `primary` for the custom themes.
 */
@Composable
fun DigitalClockProTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    accentColor: Long? = null,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    var scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    if (dark && amoledBlack) {
        scheme = scheme.copy(background = AmoledBlack, surface = AmoledBlack)
    }
    accentColor?.let { scheme = scheme.copy(primary = Color(it)) }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
