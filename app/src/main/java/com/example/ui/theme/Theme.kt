package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MechaCockpitColorScheme = darkColorScheme(
    primary = AllyBeamBlue,
    onPrimary = VoidNavy,
    primaryContainer = AllyDeepBlue,
    onPrimaryContainer = SteelText,
    secondary = SpiritGold,
    onSecondary = VoidNavy,
    secondaryContainer = Color(0xFF423300),
    onSecondaryContainer = SpiritGold,
    tertiary = EnemyCrimson,
    onTertiary = Color.White,
    background = VoidNavy,
    onBackground = SteelText,
    surface = CockpitSlate,
    onSurface = SteelText,
    surfaceVariant = PanelNavy,
    onSurfaceVariant = MutedTelemetry,
    outline = PanelBorderCyan
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MechaCockpitColorScheme,
        typography = Typography,
        content = content
    )
}
