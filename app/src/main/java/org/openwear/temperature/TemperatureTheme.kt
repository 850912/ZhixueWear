package org.openwear.temperature

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Material 3 Expressive (M3E) biothermal fallback color scheme for Wear OS round AMOLED displays.
 * Automatically yields to watch-face dynamic colors via [dynamicColorScheme] when available on Wear OS 6+.
 */
internal val ExpressiveThermalColorScheme = ColorScheme(
    primary = Color(0xFF5CE1CA),
    primaryDim = Color(0xFF3BB5A0),
    primaryContainer = Color(0xFF005046),
    onPrimary = Color(0xFF003730),
    onPrimaryContainer = Color(0xFFA2F4E3),
    secondary = Color(0xFFFFB59D),
    secondaryDim = Color(0xFFE4957B),
    secondaryContainer = Color(0xFF5C2B1B),
    onSecondary = Color(0xFF461809),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA8C8FF),
    tertiaryDim = Color(0xFF82ACEF),
    tertiaryContainer = Color(0xFF1F3E6E),
    onTertiary = Color(0xFF002F65),
    onTertiaryContainer = Color(0xFFD6E3FF),
    surfaceContainerLow = Color(0xFF141D1B),
    surfaceContainer = Color(0xFF1C2624),
    surfaceContainerHigh = Color(0xFF263330),
    onSurface = Color(0xFFE2F3EE),
    onSurfaceVariant = Color(0xFFB4CCC5),
    outline = Color(0xFF627A74),
    outlineVariant = Color(0xFF354B46),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2F3EE),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE0887E),
    errorContainer = Color(0xFF690005),
    onError = Color(0xFF560003),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
internal fun BodyTemperatureWearTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = dynamicColorScheme(context) ?: ExpressiveThermalColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
