package com.prism.music.photon

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val PhotonAmoledColorScheme = darkColorScheme(
    primary = PhotonAccentTeal,
    onPrimary = PhotonOnAccent,
    primaryContainer = PhotonAccentTeal,
    onPrimaryContainer = PhotonOnAccent,
    secondary = PhotonSecondary,
    onSecondary = PhotonOnSecondary,
    secondaryContainer = PhotonSecondaryContainer,
    onSecondaryContainer = PhotonOnSecondaryContainer,
    tertiary = PhotonTertiary,
    onTertiary = PhotonOnTertiary,
    background = PhotonCanvas,
    onBackground = PhotonPrimaryText,
    surface = PhotonCanvas,
    onSurface = PhotonPrimaryText,
    surfaceVariant = PhotonSurfaceCard,
    onSurfaceVariant = PhotonSecondaryText,
    surfaceDim = PhotonCanvas,
    surfaceBright = PhotonSurfaceBright,
    surfaceContainerLowest = PhotonSurfaceLowest,
    surfaceContainerLow = PhotonSurfaceLow,
    surfaceContainer = PhotonSurfaceCard,
    surfaceContainerHigh = PhotonSurfaceHigh,
    surfaceContainerHighest = PhotonSurfaceHighest,
    outline = PhotonOutline,
    outlineVariant = PhotonOutlineVariant,
    error = PhotonError,
    onError = PhotonOnError,
    errorContainer = PhotonErrorContainer,
    onErrorContainer = PhotonOnErrorContainer,
    surfaceTint = PhotonCanvas
)

val PhotonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun PhotonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PhotonAmoledColorScheme,
        typography = PhotonTypography,
        shapes = PhotonShapes,
        content = content
    )
}
