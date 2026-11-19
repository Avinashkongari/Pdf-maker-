package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ManhwaDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = VioletOnPrimary,
    primaryContainer = VioletContainer,
    onPrimaryContainer = VioletOnContainer,
    secondary = LavenderSecondary,
    onSecondary = LavenderOnSecondary,
    secondaryContainer = LavenderContainer,
    onSecondaryContainer = LavenderOnContainer,
    tertiary = TealTertiary,
    onTertiary = TealOnTertiary,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = TealOnContainer,
    background = CharcoalDeep,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CharcoalDeep,
    surfaceContainerLow = CharcoalSurfaceLow,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    surfaceContainerHighest = CharcoalSurfaceHighest,
    outline = CharcoalOutline,
    outlineVariant = CharcoalOutlineVariant,
    error = ErrorCoral,
    onError = VioletOnPrimary,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

val ManhwaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ManhwaPdfMakerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ManhwaDarkColorScheme,
        typography = ManhwaTypography,
        shapes = ManhwaShapes,
        content = content
    )
}
