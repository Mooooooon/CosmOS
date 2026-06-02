package com.moonlib.cosmos.ui.interaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.diary.DiaryRepository
import com.moonlib.cosmos.data.interaction.InteractionMessage
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import com.moonlib.cosmos.data.interaction.MultiInteractionEngine
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.common.conversationContentImeResize
import com.moonlib.cosmos.ui.common.rememberImeVisible
import com.moonlib.cosmos.ui.diary.DiaryAtCharacterDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiInteractionScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val interactionRepo = remember { InteractionRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }
    val settingsRepo = remember { InteractionSettingsRepository(context) }
    val chatRepo = remember { com.moonlib.cosmos.data.chat.ChatRepository(context) }

    val characterProfiles = remember {
        profileRepo.getProfiles().filter { !it.isPlayer }
    }
    val profilesById = remember(characterProfiles) { characterProfiles.associateBy { it.id } }
    val userNickname = remember { chatRepo.getUserNickname() }
    val userAvatar = remember { chatRepo.getUserAvatar() }
    val initialSelectedIds = remember(characterProfiles) {
        val saved = interactionRepo.getLastMultiParticipantIds()
            .filter { savedId -> characterProfiles.any { it.id == savedId } }
        saved.ifEmpty { characterProfiles.take(2).map { it.id } }
    }

    var selectedCharacterIds by remember { mutableStateOf(initialSelectedIds) }
    var messages by remember(selectedCharacterIds) {
        mutableStateOf(interactionRepo.getSharedMessagesForParticipants(selectedCharacterIds))
    }
    var inputText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }
    var showCharacterDialog by remember { mutableStateOf(false) }
    var isStatusCardEnabled by remember { mutableStateOf(settingsRepo.isStatusCardEnabled()) }
    var statusKeys by remember { mutableStateOf(settingsRepo.getStatusKeys()) }
    val isImeVisible = rememberImeVisible()

    fun refreshMessages() {
        messages = interactionRepo.getSharedMessagesForParticipants(selectedCharacterIds)
        isStatusCardEnabled = settingsRepo.isStatusCardEnabled()
        statusKeys = settingsRepo.getStatusKeys()
    }

    fun scrollToBottom(smooth: Boolean) {
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                if (smooth) listState.animateScrollToItem(messages.size - 1)
                else listState.scrollToItem(messages.size - 1)
            }
        }
    }

    fun saveErrorMessage(message: String) {
        val errorMsg = InteractionMessage(
            id = UUID.randomUUID().toString(),
            senderId = "system",
            content = "【系统提示】: $message",
            timestamp = VirtualTimeManager.getCurrentTimeMillis(),
            sceneId = UUID.randomUUID().toString(),
            participantIds = selectedCharacterIds
        )
        interactionRepo.saveSharedMessage(selectedCharacterIds, errorMsg)
    }

    fun restoreStatusesAt(targetTime: Long, participantIds: List<String>) {
        val diaryRepo = DiaryRepository(context)
        for (characterId in participantIds) {
            val lastInteractionMsg = interactionRepo.getMessages(characterId)
                .filter { it.timestamp <= targetTime && (it.statusMapByCharacterId?.containsKey(characterId) == true || it.statusMap != null) }
                .maxByOrNull { it.timestamp }
            val lastDiary = diaryRepo.getDiaries()
                .filter { it.timestamp <= targetTime && it.statusMap.containsKey(characterId) }
                .maxByOrNull { it.timestamp }
            val statusToRestore = when {
                lastDiary == null && lastInteractionMsg == null -> emptyMap()
                lastDiary != null && lastInteractionMsg == null -> lastDiary.statusMap[characterId] ?: emptyMap()
                lastDiary == null && lastInteractionMsg != null -> {
                    lastInteractionMsg.statusMapByCharacterId?.get(characterId) ?: lastInteractionMsg.statusMap ?: emptyMap()
                }
                lastDiary!!.timestamp > lastInteractionMsg!!.timestamp -> lastDiary.statusMap[characterId] ?: emptyMap()
                else -> lastInteractionMsg.statusMapByCharacterId?.get(characterId) ?: lastInteractionMsg.statusMap ?: emptyMap()
            }
            settingsRepo.saveCharacterStatus(characterId, statusToRestore)
        }
    }

    fun sendInteraction() {
        val text = inputText.trim()
        if (text.isBlank() || selectedCharacterIds.size < 2 || isAiGenerating) return
        inputText = ""
        interactionRepo.setLastMultiParticipantIds(selectedCharacterIds)

        val currentTime = VirtualTimeManager.getCurrentTimeMillis()
        val statusSnapshot = selectedCharacterIds.associateWith { settingsRepo.getCharacterStatus(it) }
        val userMessage = InteractionMessage(
            id = UUID.randomUUID().toString(),
            senderId = "user",
            content = text,
            timestamp = currentTime,
            sceneId = UUID.randomUUID().toString(),
            participantIds = selectedCharacterIds,
            statusMapByCharacterId = statusSnapshot
        )
        interactionRepo.saveSharedMessage(selectedCharacterIds, userMessage)
        VirtualTimeManager.updateTime(currentTime + 15000L)
        refreshMessages()
        scrollToBottom(true)

        isAiGenerating = true
        coroutineScope.launch {
            try {
                delay(1000)
                MultiInteractionEngine.getAiResponse(context, selectedCharacterIds)
            } catch (e: Exception) {
                e.printStackTrace()
                saveErrorMessage(e.localizedMessage ?: "AI 服务暂时不可用，请在系统设置中确认配置。")
            } finally {
                isAiGenerating = false
                refreshMessages()
                scrollToBottom(true)
            }
        }
    }

    LaunchedEffect(messages.size, selectedCharacterIds) {
        refreshMessages()
        scrollToBottom(false)
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            delay(250)
            scrollToBottom(false)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Column {
                        Text(
                            text = "多人互动",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (isAiGenerating) {
                                "大家正在回应..."
                            } else {
                                selectedCharacterIds.mapNotNull { profilesById[it]?.name }.joinToString("、")
                                    .ifBlank { "请选择参与人物" }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAiGenerating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            MultiInteractionInputBar(
                inputText = inputText,
                selectedCharacterIds = selectedCharacterIds,
                characterProfiles = characterProfiles,
                isAiGenerating = isAiGenerating,
                onInputChange = { inputText = it },
                onRemoveCharacter = { characterId ->
                    selectedCharacterIds = selectedCharacterIds.filter { it != characterId }
                    interactionRepo.setLastMultiParticipantIds(selectedCharacterIds)
                    refreshMessages()
                },
                onPickCharacters = { showCharacterDialog = true },
                onSend = { sendInteraction() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .conversationContentImeResize()
        ) {
            if (isStatusCardEnabled && statusKeys.isNotEmpty()) {
                selectedCharacterIds.forEach { characterId ->
                    val character = profilesById[characterId]
                    if (character != null) {
                        InteractionStatusCard(
                            characterName = character.name,
                            statusKeys = statusKeys,
                            charStatus = settingsRepo.getCharacterStatus(character.id)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id }
                ) { index, msg ->
                    val showTimeLabel = index == 0 || msg.timestamp - messages[index - 1].timestamp > 3 * 60 * 1000
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (showTimeLabel) {
                            TimeLabel(
                                timestamp = msg.timestamp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }

                        when (msg.senderId) {
                            "user" -> {
                                UserInteractionRow(
                                    content = msg.content,
                                    userNickname = userNickname,
                                    userAvatar = userAvatar,
                                    onDelete = {
                                        interactionRepo.deleteSharedMessage(msg)
                                        refreshMessages()
                                    },
                                    onResend = {
                                        VirtualTimeManager.rollbackTime(msg.timestamp)
                                        restoreStatusesAt(msg.timestamp, msg.participantIds)
                                        interactionRepo.deleteSharedMessagesAfter(msg)
                                        refreshMessages()
                                        isAiGenerating = true
                                        coroutineScope.launch {
                                            try {
                                                delay(1000)
                                                MultiInteractionEngine.getAiResponse(context, msg.participantIds)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                saveErrorMessage(e.localizedMessage ?: "AI 服务暂时不可用，请在系统设置中确认配置。")
                                            } finally {
                                                isAiGenerating = false
                                                refreshMessages()
                                                scrollToBottom(true)
                                            }
                                        }
                                    }
                                )
                            }
                            "system" -> {
                                SystemMessageRow(
                                    content = msg.content,
                                    onDelete = {
                                        interactionRepo.deleteSharedMessage(msg)
                                        refreshMessages()
                                    }
                                )
                            }
                            else -> {
                                val sender = profilesById[msg.senderId]
                                CharacterInteractionRow(
                                    messageId = msg.id,
                                    content = msg.content,
                                    characterName = sender?.name ?: "角色",
                                    characterAvatar = sender?.avatar ?: "",
                                    voicePlaybackState = null,
                                    onDelete = {
                                        interactionRepo.deleteSharedMessage(msg)
                                        refreshMessages()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCharacterDialog) {
        DiaryAtCharacterDialog(
            onDismissRequest = { showCharacterDialog = false },
            availableCharacters = characterProfiles,
            selectedCharacterIds = selectedCharacterIds,
            onSelectedCharactersChange = { ids ->
                selectedCharacterIds = ids
                interactionRepo.setLastMultiParticipantIds(ids)
                refreshMessages()
            }
        )
    }
}
