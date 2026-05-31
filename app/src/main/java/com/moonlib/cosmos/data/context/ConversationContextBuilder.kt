package com.moonlib.cosmos.data.context

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryItem
import com.moonlib.cosmos.data.ai.AiHistorySource
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

    fun buildWideHistoryForCharacter(
        context: Context,
        charProfile: CharacterProfile,
        maxContextSize: Int,
        senderNameResolver: (String) -> String = { it }
    ): List<AiHistoryItem> {
        return buildForCharacter(context, charProfile, maxContextSize).map { msg ->
            AiHistoryItem(
                senderId = msg.senderId,
                senderName = senderNameResolver(msg.senderId),
                content = msg.content,
                timestamp = msg.timestamp,
                source = when (msg.source) {
                    MergedMessageSource.CHAT -> AiHistorySource.CHAT
                    MergedMessageSource.INTERACTION -> AiHistorySource.INTERACTION
                    MergedMessageSource.DIARY -> AiHistorySource.DIARY
                    MergedMessageSource.TWITTER -> AiHistorySource.TWITTER
                    MergedMessageSource.MOMENT -> AiHistorySource.MOMENT
                }
            )
        }
    }

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
                        senderId = "diary",
                        content = diary.summary,
                        timestamp = diary.contextTimestamp(),
                        isOnline = false,
                        source = MergedMessageSource.DIARY
                    ),
                    fullDiaryContent = diary.content
                )
            }

        // 获取公共推特动态与回复，并合入全局历史记忆
        val twitterRepo = com.moonlib.cosmos.data.twitter.TwitterRepository(context)
        val twitterMsgs = twitterRepo.getTweets().map { tweet ->
            val authorProfile = twitterRepo.getProfile(tweet.authorId)
            val authorUsername = authorProfile?.username ?: tweet.authorId
            val parentTweet = tweet.parentId?.let { twitterRepo.getTweet(it) }
            val parentProfile = parentTweet?.let { twitterRepo.getProfile(it.authorId) }
            val parentUsername = parentProfile?.username
            
            val formattedContent = buildString {
                if (tweet.imagePath != null) {
                    append("[发布了图文] ")
                }
                if (parentUsername != null) {
                    append("回复 @$parentUsername: ")
                }
                append(tweet.content)
            }
            ContextCandidate(
                message = MergedMessage(
                    senderId = tweet.authorId,
                    content = "@$authorUsername: $formattedContent",
                    timestamp = tweet.timestamp,
                    isOnline = false,
                    source = MergedMessageSource.TWITTER
                )
            )
        }

        // 获取朋友圈动态与评论，并合入全局历史记忆
        val momentRepo = com.moonlib.cosmos.data.chat.MomentRepository(context)
        val momentMsgs = momentRepo.getMoments().map { moment ->
            val authorProfile = momentRepo.getProfile(moment.authorId)
            val authorNickname = authorProfile?.nickname ?: moment.authorId
            val parentMoment = moment.parentId?.let { momentRepo.getMoment(it) }
            val parentProfile = parentMoment?.let { momentRepo.getProfile(it.authorId) }
            val parentNickname = parentProfile?.nickname
            
            val formattedContent = buildString {
                if (moment.imagePath != null) {
                    val desc = moment.imagePath.removePrefix("simulated_image:")
                    append("[发布了照片动态：“$desc”] ")
                }
                if (moment.videoPath != null) {
                    val desc = moment.videoPath.removePrefix("simulated_video:")
                    append("[发布了视频动态：“$desc”] ")
                }
                if (parentNickname != null) {
                    append("回复了 $parentNickname 的动态评论: ")
                }
                append(moment.content)
            }
            ContextCandidate(
                message = MergedMessage(
                    senderId = moment.authorId,
                    content = "$authorNickname: $formattedContent",
                    timestamp = moment.timestamp,
                    isOnline = false,
                    source = MergedMessageSource.MOMENT
                )
            )
        }

        val sortedCandidates = (formattedOnlineMsgs + offlineMsgs + diaryMsgs + twitterMsgs + momentMsgs).sortedBy { it.message.timestamp }
        val anchoredCandidates = sortedCandidates.keepOnlyContextBeforeCurrentUserInput()
        val fullDiaryIndex = anchoredCandidates.indexOfDiaryToExpandForCurrentReply()
        val mergedMessages = anchoredCandidates.mapIndexed { index, candidate ->
            if (index == fullDiaryIndex) {
                candidate.message.copy(content = candidate.fullDiaryContent ?: candidate.message.content)
            } else {
                candidate.message
            }
        }
        return mergedMessages.takeLast(maxContextSize)
    }

    private data class ContextCandidate(
        val message: MergedMessage,
        val fullDiaryContent: String? = null
    )

    private fun List<ContextCandidate>.keepOnlyContextBeforeCurrentUserInput(): List<ContextCandidate> {
        val currentUserInputIndex = indexOfLast { it.message.isCurrentUserInput() }
        if (currentUserInputIndex < 0) return this

        val currentUserInput = this[currentUserInputIndex]
        val historyBeforeInput = filterIndexed { index, candidate ->
            index != currentUserInputIndex && candidate.message.timestamp <= currentUserInput.message.timestamp
        }
        return historyBeforeInput + currentUserInput
    }

    private fun List<ContextCandidate>.indexOfDiaryToExpandForCurrentReply(): Int {
        if (isEmpty()) return -1

        val currentUserInputIndex = indexOfLast { it.message.isCurrentUserInput() }
        val candidateIndex = if (currentUserInputIndex >= 0) currentUserInputIndex - 1 else lastIndex
        if (candidateIndex < 0) return -1
        return if (this[candidateIndex].message.source == MergedMessageSource.DIARY) candidateIndex else -1
    }

    private fun MergedMessage.isCurrentUserInput(): Boolean {
        return senderId == "user" && (source == MergedMessageSource.CHAT || source == MergedMessageSource.INTERACTION)
    }

    private fun DiaryEntry.contextTimestamp(): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(virtualTime)?.time ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
}
