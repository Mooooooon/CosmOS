package com.moonlib.cosmos.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository

/**
 * 档案 APP 主路由入口容器
 * 
 * 职责单一：负责子页面的路由调度、仓库数据的同步与加载。
 */
@Composable
fun ProfileAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CharacterProfileRepository(context) }

    var currentScreen by remember { mutableStateOf<ProfileScreenState>(ProfileScreenState.List) }
    var profiles by remember { mutableStateOf(emptyList<CharacterProfile>()) }

    // ── 核心数据刷新 ──────────────────────────────────────
    val refreshProfiles = {
        profiles = repository.getProfiles()
    }

    LaunchedEffect(Unit) {
        refreshProfiles()
    }

    // ── 物理/手势返回键在主页时的处理 ─────────────────────
    BackHandler(enabled = currentScreen is ProfileScreenState.List) {
        onGoBack()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "ProfileAppScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            is ProfileScreenState.List -> {
                ProfileListScreen(
                    profiles = profiles,
                    onBackClick = onGoBack,
                    onAddClick = { isPlayer ->
                        currentScreen = ProfileScreenState.Edit(
                            profileId = null,
                            isPlayer = isPlayer
                        )
                    },
                    onEditClick = { id, isPlayer ->
                        currentScreen = ProfileScreenState.Edit(
                            profileId = id,
                            isPlayer = isPlayer
                        )
                    }
                )
            }

            is ProfileScreenState.Edit -> {
                val initialProfile = remember(screen.profileId) {
                    if (screen.profileId != null) {
                        profiles.firstOrNull { it.id == screen.profileId }
                    } else {
                        null
                    }
                }

                ProfileEditScreen(
                    initialProfile = initialProfile,
                    profiles = profiles,
                    isPlayer = screen.isPlayer,
                    onBackClick = {
                        currentScreen = ProfileScreenState.List
                    },
                    onSaveClick = { profile ->
                        repository.saveProfile(profile)
                        refreshProfiles()
                        currentScreen = ProfileScreenState.List
                    },
                    onDeleteClick = { id ->
                        repository.deleteProfile(id)
                        refreshProfiles()
                        currentScreen = ProfileScreenState.List
                    }
                )
            }
        }
    }
}
