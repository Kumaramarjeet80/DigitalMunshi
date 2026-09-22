package com.digitalmunshi.pos.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNavy,
    onPrimary = Color.White,
    primaryContainer = PrimarySlate,
    onPrimaryContainer = Color.White,
    secondary = BrandEmerald,
    onSecondary = Color.White,
    secondaryContainer = BrandEmeraldLight,
    onSecondaryContainer = BrandEmeraldDark,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorCrimsonLight,
    onErrorContainer = ErrorCrimson
)

val PosShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun DigitalMunshiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        shapes = PosShapes,
        content = content
    )
}
