package com.moonlib.cosmos.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.settings.AiLog
import com.moonlib.cosmos.data.settings.AiLogRepository

/**
 * 设置内部的子页面路由状态
 */
sealed interface SettingsScreenState {
    object Main : SettingsScreenState
    object ProfileList : SettingsScreenState
    data class AddEditProfile(val profileId: String?) : SettingsScreenState
    object ThemeSettings : SettingsScreenState
    object AiLogsList : SettingsScreenState
    data class AiLogDetail(val logId: String) : SettingsScreenState
}

/**
 * 设置 APP 主容器入口
 *
 * 职责单一：负责管理设置子页面之间的跳转状态，并作为数据总线与底层持久化仓库交互
 */
@Composable
fun SettingsAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { AiConfigRepository(context) }

    // ── 核心状态管理 ──────────────────────────────────────────
    var currentScreen by remember { mutableStateOf<SettingsScreenState>(SettingsScreenState.Main) }
    var profiles by remember { mutableStateOf(emptyList<AiProfile>()) }
    var activeProfileName by remember { mutableStateOf("未配置") }

    // ── 核心数据刷新逻辑 ──────────────────────────────────────
    val refreshData = {
        val list = repository.getProfiles()
        profiles = list
        val active = repository.getActiveProfile()
        activeProfileName = if (active != null) {
            "${active.name} (${active.modelName})"
        } else {
            "未配置 (点击配置)"
        }
    }

    // 首次启动及组件进入时刷新
    LaunchedEffect(Unit) {
        refreshData()
    }

    // ── 系统物理/手势返回键适配 ────────────────────────────────
    BackHandler(enabled = true) {
        when (currentScreen) {
            is SettingsScreenState.Main -> onGoBack()
            is SettingsScreenState.ProfileList -> currentScreen = SettingsScreenState.Main
            is SettingsScreenState.AddEditProfile -> currentScreen = SettingsScreenState.ProfileList
            is SettingsScreenState.ThemeSettings -> currentScreen = SettingsScreenState.Main
            is SettingsScreenState.AiLogsList -> currentScreen = SettingsScreenState.Main
            is SettingsScreenState.AiLogDetail -> currentScreen = SettingsScreenState.AiLogsList
        }
    }

    // ── 子页面渲染调度 ────────────────────────────────────────
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "SettingsNavigationAnim",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            // ── 1. 设置主页 ──────────────────────────────────────────
            is SettingsScreenState.Main -> {
                SettingsMainScreen(
                    activeProfileName = activeProfileName,
                    onModelServiceClick = {
                        refreshData()
                        currentScreen = SettingsScreenState.ProfileList
                    },
                    onThemeClick = {
                        currentScreen = SettingsScreenState.ThemeSettings
                    },
                    onLogsClick = {
                        currentScreen = SettingsScreenState.AiLogsList
                    }
                )
            }

            // ── 2. 配置文件列表页 ────────────────────────────────────
            is SettingsScreenState.ProfileList -> {
                ModelServicesListScreen(
                    profiles = profiles,
                    onBackClick = {
                        currentScreen = SettingsScreenState.Main
                    },
                    onAddClick = {
                        currentScreen = SettingsScreenState.AddEditProfile(profileId = null)
                    },
                    onEditClick = { id ->
                        currentScreen = SettingsScreenState.AddEditProfile(profileId = id)
                    },
                    onDeleteClick = { id ->
                        repository.deleteProfile(id)
                        refreshData()
                    },
                    onSelectActive = { id ->
                        repository.setActiveProfile(id)
                        refreshData()
                    }
                )
            }

            // ── 3. 添加/编辑页面 ─────────────────────────────────────
            is SettingsScreenState.AddEditProfile -> {
                val initialProfile = remember(screen.profileId) {
                    if (screen.profileId != null) {
                        profiles.firstOrNull { it.id == screen.profileId }
                    } else {
                        null
                    }
                }

                ModelServiceConfigScreen(
                    initialProfile = initialProfile,
                    onBackClick = {
                        currentScreen = SettingsScreenState.ProfileList
                    },
                    onSaveClick = { profile ->
                        repository.saveProfile(profile)
                        refreshData()
                        currentScreen = SettingsScreenState.ProfileList
                    }
                )
            }

            // ── 4. 个性化主题设置页面 ──────────────────────────────────
            is SettingsScreenState.ThemeSettings -> {
                ThemeSettingsScreen(
                    onBackClick = {
                        currentScreen = SettingsScreenState.Main
                    }
                )
            }

            // ── 5. AI 通讯日志列表页面 ──────────────────────────────────
            is SettingsScreenState.AiLogsList -> {
                val logsRepo = remember { AiLogRepository(context) }
                var logsList by remember { mutableStateOf(emptyList<AiLog>()) }

                LaunchedEffect(Unit) {
                    logsList = logsRepo.getLogs()
                }

                AiLogsListScreen(
                    logs = logsList,
                    onBackClick = {
                        currentScreen = SettingsScreenState.Main
                    },
                    onLogClick = { id ->
                        currentScreen = SettingsScreenState.AiLogDetail(id)
                    },
                    onClearLogs = {
                        logsRepo.clearLogs()
                        logsList = emptyList()
                    }
                )
            }

            // ── 6. AI 通讯日志详情页面 ──────────────────────────────────
            is SettingsScreenState.AiLogDetail -> {
                val logsRepo = remember { AiLogRepository(context) }
                val log = remember(screen.logId) {
                    logsRepo.getLogs().firstOrNull { it.id == screen.logId }
                }

                AiLogDetailScreen(
                    log = log,
                    onBackClick = {
                        currentScreen = SettingsScreenState.AiLogsList
                    }
                )
            }
        }
    }
}
