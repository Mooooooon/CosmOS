package com.moonlib.cosmos.data.context

import android.content.Context
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.diary.DiaryEntry
import com.moonlib.cosmos.data.diary.DiaryRepository
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.MergedMessage
import com.moonlib.cosmos.data.interaction.MergedMessageSource
import com.moonlib.cosmos.data.profile.CharacterProfile
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 对话上下文构建器
 *
 * 职责单一：按时间线合并线上聊天、线下互动与剧情日记，并应用上下文数量裁剪规则。
 */
object ConversationContextBuilder {

    fun buildForCharacter(
        context: Context,
        charProfile: CharacterProfile,
        maxContextSize: Int
    ): List<MergedMessage> {
        val chatRepo = ChatRepository(context)
        val interactionRepo = InteractionRepository(context)
        val diaryRepo = DiaryRepository(context)

        val contact = chatRepo.getContacts().firstOrNull { it.characterId == charProfile.id }
        val onlineMsgs = if (contact != null) chatRepo.getMessages(contact.id) else emptyList()
        val formattedOnlineMsgs = onlineMsgs.map { msg ->
            val formattedContent = when (msg.type) {
                "image" -> "[发送了图片：${msg.content}]"
                "video" -> "[发送了视频：${msg.content}]"
                "red_packet" -> "[发送了红包：${msg.content}元，留言：${msg.extra ?: "恭喜发财，大吉大利"}]"
                "transfer" -> "[发送了转账：${msg.content}元]"
                "location" -> "[发送了位置：${msg.content}]"
                else -> msg.content
            }
            ContextCandidate(
                message = MergedMessage(
                    senderId = msg.senderId,
                    content = formattedContent,
                    timestamp = msg.timestamp,
                    isOnline = true,
                    source = MergedMessageSource.CHAT
                )
            )
        }

        val offlineMsgs = interactionRepo.getMessages(charProfile.id).map { msg ->
            ContextCandidate(
                message = MergedMessage(
                    senderId = msg.senderId,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    isOnline = false,
                    source = MergedMessageSource.INTERACTION
                )
            )
        }

        val diaryMsgs = diaryRepo.getDiaries()
            .filter { it.involvedCharacterIds.contains(charProfile.id) }
            .map { diary ->
                ContextCandidate(
                    message = MergedMessage(
                        senderId = "user",
                        content = diary.summary,
                        timestamp = diary.contextTimestamp(),
                        isOnline = false,
                        source = MergedMessageSource.DIARY
                    ),
                    fullDiaryContent = diary.content
                )
            }

        val sortedCandidates = (formattedOnlineMsgs + offlineMsgs + diaryMsgs).sortedBy { it.message.timestamp }
        val fullDiaryIndex = sortedCandidates.indexOfLatestDiaryBeforeCurrentReply()
        return sortedCandidates.mapIndexed { index, candidate ->
            if (index == fullDiaryIndex) {
                candidate.message.copy(content = candidate.fullDiaryContent ?: candidate.message.content)
            } else {
                candidate.message
            }
        }.takeLast(maxContextSize)
    }

    private data class ContextCandidate(
        val message: MergedMessage,
        val fullDiaryContent: String? = null
    )

    private fun List<ContextCandidate>.indexOfLatestDiaryBeforeCurrentReply(): Int {
        if (isEmpty()) return -1

        val anchorIndex = if (last().message.senderId == "user" && last().message.source != MergedMessageSource.DIARY) {
            lastIndex - 1
        } else {
            lastIndex
        }

        if (anchorIndex < 0) return -1
        return if (this[anchorIndex].message.source == MergedMessageSource.DIARY) anchorIndex else -1
    }

    private fun DiaryEntry.contextTimestamp(): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(virtualTime)?.time ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
}
