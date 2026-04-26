package com.paykeyfear.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary              = AccentGreen,
    onPrimary            = SurfaceBg,
    primaryContainer     = AccentGreenDim,
    onPrimaryContainer   = AccentGreen,
    secondary            = Blue,
    onSecondary          = SurfaceBg,
    secondaryContainer   = BlueDim,
    onSecondaryContainer = Blue,
    tertiary             = AmberColor,
    onTertiary           = SurfaceBg,
    error                = DangerColor,
    onError              = SurfaceBg,
    errorContainer       = DangerDim,
    onErrorContainer     = DangerColor,
    background           = SurfaceBg,
    onBackground         = TextPrimary,
    surface              = SurfaceCard,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceCard2,
    onSurfaceVariant     = TextMuted,
    outline              = BorderColor,
    outlineVariant       = BorderColor,
)

@Composable
fun PaykeyfearTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography  = AppTypography,
        content     = content,
    )
}
