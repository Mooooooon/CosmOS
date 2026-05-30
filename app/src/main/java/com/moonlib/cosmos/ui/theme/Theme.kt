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
    primary            = NebulaPurple,
    onPrimary          = StarWhite,
    secondary          = NebulaCyan,
    onSecondary        = SpaceDeepBlack,
    tertiary           = NebulaBlue,
    background         = SpaceDeepBlack,
    onBackground       = StarWhite,
    surface            = SurfaceDark,
    onSurface          = StarWhite,
    surfaceVariant     = SurfaceCard,
    outline            = SurfaceBorder,
    // 语义化定制：全屏媒体预览纯色卡片 (深色使用深紫背景与淡紫文字)
    primaryContainer   = Color(0xFF2C1E4D),
    onPrimaryContainer = Color(0xFFE5D5FF),
    // 语义化定制：红包功能色板 (深色下红包底色为 error，金币为 errorContainer，金币文字为 onErrorContainer)
    error              = Color(0xFFFA5151),
    errorContainer     = Color(0xFFFFD54F),
    onErrorContainer   = Color(0xFFD32F2F),
    // 语义化定制：转账功能色板 (深色下转账底色为 tertiaryContainer)
    tertiaryContainer  = Color(0xFFFA9D3B),
)

// CosmOS 优雅浅色主题色板
private val CosmOSLightColorScheme = lightColorScheme(
    primary            = NebulaBlue,
    onPrimary          = Color.White,
    secondary          = NebulaCyan,
    onSecondary        = SpaceDeepBlack,
    tertiary           = NebulaBlue,
    background         = LightBackground,
    onBackground       = LightTextPrimary,
    surface            = LightSurface,
    onSurface          = LightTextPrimary,
    surfaceVariant     = LightSurfaceCard,
    outline            = LightSurfaceBorder,
    // 语义化定制：全屏媒体预览纯色卡片 (浅色使用浅蓝背景与深蓝文字)
    primaryContainer   = Color(0xFFD6ECF8),
    onPrimaryContainer = Color(0xFF1E5680),
    // 语义化定制：红包功能色板 (浅色下红包色板与深色一致)
    error              = Color(0xFFFA5151),
    errorContainer     = Color(0xFFFFD54F),
    onErrorContainer   = Color(0xFFD32F2F),
    // 语义化定制：转账功能色板 (浅色下转账色板与深色一致)
    tertiaryContainer  = Color(0xFFFA9D3B),
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