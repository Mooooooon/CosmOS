package com.moonlib.cosmos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局主题配置持有者
 */
data class ThemeConfig(
    val isDark: Boolean,
    val setDarkTheme: (Boolean) -> Unit
)

/**
 * 全局主题 Local 上下文
 */
val LocalThemeConfig = staticCompositionLocalOf {
    ThemeConfig(isDark = true, setDarkTheme = {})
}

// CosmOS 深色主题色板
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

// CosmOS 优雅浅色主题色板
private val CosmOSLightColorScheme = lightColorScheme(
    primary        = NebulaBlue,
    onPrimary      = Color.White,
    secondary      = NebulaCyan,
    onSecondary    = SpaceDeepBlack,
    tertiary       = NebulaBlue,
    background     = LightBackground,
    onBackground   = LightTextPrimary,
    surface        = LightSurface,
    onSurface      = LightTextPrimary,
    surfaceVariant = LightSurfaceCard,
    outline        = LightSurfaceBorder,
)

@Composable
fun CosmOSTheme(isDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = if (isDarkTheme) CosmOSColorScheme else CosmOSLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}