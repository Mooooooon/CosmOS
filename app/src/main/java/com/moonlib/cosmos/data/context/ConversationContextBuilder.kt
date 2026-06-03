package com.moonlib.cosmos.data.context

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryItem
import com.moonlib.cosmos.data.ai.AiHistorySource
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.Moment
import com.moonlib.cosmos.data.chat.MomentRepository
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.diary.DiaryEntry
import com.moonlib.cosmos.data.diary.DiaryRepository
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.memory.MemoryRepository
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterRepository
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 对话上下文构建器
 *
 * 职责单一：按时间线合并线上聊天、线下互动与剧情日记，并应用上下文数量裁剪规则。
 */
object ConversationContextBuilder {

    fun buildWideHistoryForCharacters(
        context: Context,
        charProfiles: List<CharacterProfile>,
        maxContextSize: Int,
        playerName: String,
        diariesOverride: List<DiaryEntry>? = null
    ): List<AiHistoryItem> {
        val involvedCharacterIds = charProfiles.map { it.id }.toSet()
        val characterNameById = charProfiles.associate { it.id to it.name }
        val chatRepo = ChatRepository(context)
        val interactionRepo = InteractionRepository(context)
        val diaryRepo = DiaryRepository(context)
        val twitterRepo = TwitterRepository(context)
        val momentRepo = MomentRepository(context)
        val allProfiles = CharacterProfileRepository(context).getProfiles()
        val allCharacterNameById = allProfiles.associate { it.id to it.name }

        val contactsByCharacterId = chatRepo.getContacts()
            .filter { it.characterId in involvedCharacterIds }
            .associateBy { it.characterId }
        val characterIdByContactId = contactsByCharacterId.values.associate { it.id to it.characterId }

        val chatItems = contactsByCharacterId.values.flatMap { contact ->
            chatRepo.getMessages(contact.id).map { message ->
                val senderCharacterId = characterIdByContactId[message.senderId] ?: contact.characterId
                AiHistoryItem(
                    senderId = message.senderId,
                    senderName = if (message.senderId == "user") playerName else characterNameById[senderCharacterId] ?: contact.nickname,
                    content = message.formatForHistory(),
                    timestamp = message.timestamp,
                    source = AiHistorySource.CHAT
                )
            }
        }

        val interactionMessages = InteractionContextHistoryCollector.collectForCharacters(
            interactionRepo = interactionRepo,
            targetCharacterIds = involvedCharacterIds,
            allCharacterIds = allProfiles.filterNot { it.isPlayer }.map { it.id }.toSet()
        )

        val interactionItems = InteractionActionHistoryFilter
            .filterForHistory(interactionMessages)
            .map { message ->
                AiHistoryItem(
                    senderId = message.senderId,
                    senderName = when (message.senderId) {
                        "user" -> playerName
                        "system" -> "系统"
                        else -> allCharacterNameById[message.senderId]
                            ?: characterNameById[message.senderId].orEmpty().ifBlank { "角色" }
                    },
                    content = message.content,
                    timestamp = message.timestamp,
                    source = AiHistorySource.INTERACTION
                )
            }

        val diaryItems = (diariesOverride ?: diaryRepo.getDiaries())
            .filter { diary -> diary.involvedCharacterIds.any { it in involvedCharacterIds } }
            .map { diary ->
                HistoryCandidate(
                    item = AiHistoryItem(
                        senderId = "diary",
                        senderName = "剧情日记",
                        content = diary.summary,
                        timestamp = diary.contextTimestamp(),
                        source = AiHistorySource.DIARY
                    ),
                    fullDiaryContent = diary.content
                )
            }

        val tweets = twitterRepo.getTweets()
        val tweetById = tweets.associateBy { it.id }
        val twitterItems = tweets
            .filter { tweet -> tweet.isRelevantTo(involvedCharacterIds, tweetById) }
            .map { tweet ->
                val profile = twitterRepo.getProfile(tweet.authorId)
                AiHistoryItem(
                    senderId = tweet.authorId,
                    senderName = if (tweet.authorId == "user") playerName else characterNameById[tweet.authorId] ?: profile?.nickname ?: tweet.authorId,
                    content = tweet.formatForHistory(twitterRepo, tweetById),
                    timestamp = tweet.timestamp,
                    source = AiHistorySource.TWITTER
                )
            }

        val moments = momentRepo.getMoments()
        val momentById = moments.associateBy { it.id }
        val momentItems = moments
            .filter { moment -> moment.isRelevantTo(involvedCharacterIds, momentById) }
            .map { moment ->
                val profile = momentRepo.getProfile(moment.authorId)
                AiHistoryItem(
                    senderId = moment.authorId,
                    senderName = if (moment.authorId == "user") playerName else characterNameById[moment.authorId] ?: profile?.nickname ?: moment.authorId,
                    content = moment.formatForHistory(momentRepo, momentById),
                    timestamp = moment.timestamp,
                    source = AiHistorySource.MOMENT
                )
            }

        val candidates = (
            chatItems.map { HistoryCandidate(it) } +
                interactionItems.map { HistoryCandidate(it) } +
                diaryItems +
                twitterItems.map { HistoryCandidate(it) } +
                momentItems.map { HistoryCandidate(it) }
            ).sortedBy { it.item.timestamp }

        val fullDiaryIndex = candidates.indexOfLast { it.item.source == AiHistorySource.DIARY }
            .takeIf { it == candidates.lastIndex }
            ?: -1

        return candidates.mapIndexed { index, candidate ->
            if (index == fullDiaryIndex) {
                candidate.item.copy(content = candidate.fullDiaryContent ?: candidate.item.content)
            } else {
                candidate.item
            }
        }.takeLast(maxContextSize)
    }

    fun buildMemoryListForCharacters(
        context: Context,
        charProfiles: List<CharacterProfile>,
        maxMemoriesPerCharacter: Int = 30
    ): String {
        val involvedCharacterIds = charProfiles.map { it.id }.toSet()
        if (involvedCharacterIds.isEmpty()) return ""

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val memories = MemoryRepository(context).getMemories()
            .filter { memory ->
                memory.isContextEnabled && memory.characterIds.any { it in involvedCharacterIds }
            }
        if (memories.isEmpty()) return ""

        return charProfiles.joinToString("\n\n") { profile ->
            val characterMemories = memories
                .filter { profile.id in it.characterIds }
                .sortedBy { it.updatedAt }
                .takeLast(maxMemoriesPerCharacter)

            if (characterMemories.isEmpty()) {
                ""
            } else {
                val body = characterMemories.joinToString("\n") { memory ->
                    MemoryContextFormatter.formatForGroupedList(
                        memory = memory,
                        timeText = formatter.format(java.util.Date(memory.updatedAt))
                    )
                }
                "【${profile.name}】\n$body"
            }
        }.split("\n\n").filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private data class HistoryCandidate(
        val item: AiHistoryItem,
        val fullDiaryContent: String? = null
    )

    private fun DiaryEntry.contextTimestamp(): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(virtualTime)?.time ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }

    private fun ChatMessage.formatForHistory(): String {
        return when (type) {
            "image" -> "[发送了图片：$content]"
            "video" -> "[发送了视频：$content]"
            "voice" -> "[发送了语音：$content]"
            "red_packet" -> "[发送了红包：$content 元，留言：${extra ?: "恭喜发财，大吉大利"}]"
            "transfer" -> "[发送了转账：$content 元]"
            "location" -> "[发送了位置：$content]"
            else -> content
        }
    }

    private fun Tweet.isRelevantTo(involvedCharacterIds: Set<String>, tweetById: Map<String, Tweet>): Boolean {
        if (authorId in involvedCharacterIds) return true
        val parent = parentId?.let { tweetById[it] }
        return parent?.authorId in involvedCharacterIds
    }

    private fun Tweet.formatForHistory(
        twitterRepo: TwitterRepository,
        tweetById: Map<String, Tweet>
    ): String {
        val parent = parentId?.let { tweetById[it] }
        val parentProfile = parent?.let { twitterRepo.getProfile(it.authorId) }
        return buildString {
            if (imagePath != null) append("[发布了图文] ")
            if (parentProfile != null) append("回复 @${parentProfile.username}: ")
            append(content)
        }
    }

    private fun Moment.isRelevantTo(involvedCharacterIds: Set<String>, momentById: Map<String, Moment>): Boolean {
        if (authorId in involvedCharacterIds) return true
        return hasAncestorByAuthor(involvedCharacterIds, momentById)
    }

    private fun Moment.hasAncestorByAuthor(
        authorIds: Set<String>,
        momentById: Map<String, Moment>
    ): Boolean {
        val visited = mutableSetOf<String>()
        var currentParentId = parentId

        while (currentParentId != null && visited.add(currentParentId)) {
            val parent = momentById[currentParentId] ?: return false
            if (parent.authorId in authorIds) return true
            currentParentId = parent.parentId
        }

        return false
    }

    private fun Moment.formatForHistory(
        momentRepo: MomentRepository,
        momentById: Map<String, Moment>
    ): String {
        val parent = parentId?.let { momentById[it] }
        val parentProfile = parent?.let { momentRepo.getProfile(it.authorId) }
        return buildString {
            imagePath?.removePrefix("simulated_image:")?.let { append("[发布了照片动态：“$it”] ") }
            videoPath?.removePrefix("simulated_video:")?.let { append("[发布了视频动态：“$it”] ") }
            if (parentProfile != null) append("回复了 ${parentProfile.nickname} 的动态评论: ")
            append(content)
        }
    }
}
