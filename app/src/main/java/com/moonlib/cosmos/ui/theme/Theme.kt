package com.moonlib.cosmos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// CosmOS 固定使用深色主题，不跟随系统，不使用 Dynamic Color
private val CosmOSColorScheme = darkColorScheme(
    primary        = NebulaPurple,
    onPrimary      = StarWhite,
    secondary      = NebulaCyan,
    onSecondary    = SpaceDeepBlack,
    tertiary       = NebulaBlue,
    background     = SpaceDeepBlack,
    onBackground   = StarWhite,
    surface        = SurfaceDark,
    onSurface      = StarWhite,
    surfaceVariant = SurfaceCard,
    outline        = SurfaceBorder,
)

@Composable
fun CosmOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CosmOSColorScheme,
        typography  = Typography,
        content     = content,
    )
}