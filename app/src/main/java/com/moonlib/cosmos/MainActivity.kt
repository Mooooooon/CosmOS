package com.moonlib.cosmos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import com.moonlib.cosmos.data.settings.ThemeSettingsRepository
import com.moonlib.cosmos.ui.desktop.DesktopScreen
import com.moonlib.cosmos.ui.theme.CosmOSTheme
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import com.moonlib.cosmos.ui.theme.ThemeConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themeRepository = ThemeSettingsRepository(this)

        // 内容延伸到全屏（EdgeToEdge）
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            var isDark by remember { mutableStateOf(themeRepository.isDarkTheme()) }

            CompositionLocalProvider(
                LocalThemeConfig provides ThemeConfig(
                    isDark = isDark,
                    setDarkTheme = { dark ->
                        themeRepository.setDarkTheme(dark)
                        isDark = dark
                    }
                )
            ) {
                CosmOSTheme(isDarkTheme = isDark) {
                    DesktopScreen()
                }
            }
        }

        // 在 DecorView 附着后尝试执行隐藏
        window.decorView.post {
            hideSystemStatusBar()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemStatusBar()
        }
    }

    private fun hideSystemStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}