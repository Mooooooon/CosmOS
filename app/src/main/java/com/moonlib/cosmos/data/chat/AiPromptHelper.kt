package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.MergedMessage

/**
 * AI 提示词与历史记忆处理公共类
 * 职责单一：负责结合当前虚拟时间、人物设定以及全局设置，生成针对不同场景特化的 System Prompt，并融合同步、过滤和切片全局上下文历史消息
 */
data class AiPromptData(
    val systemPrompt: String,
    val recentMergedHistory: List<MergedMessage>
)

object AiPromptHelper {

    /**
     * 构建针对特定场景定制的 System Prompt 及经过全局设置切片的合并历史
     */
    fun buildPromptAndHistory(
        context: Context,
        charProfile: CharacterProfile,
        sceneType: AiSceneType,
        chatNickname: String? = null,
        chatSignature: String? = null
    ): AiPromptData {
        val chatRepo = ChatRepository(context)
        val interactionRepo = InteractionRepository(context)
        val profileRepo = CharacterProfileRepository(context)

        // 1. 获取全局上下文数量设置
        val aiSettingsRepo = AiSettingsRepository(context)
        val maxContextSize = aiSettingsRepo.getMaxContextSize()

        // 2. 获取全局系统提示词基底
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()

        val userNickname = chatRepo.getUserNickname()
        
        // 获取玩家（用户）真实姓名
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val playerRealName = playerProfile?.name ?: userNickname

        // 动态替换人设中的 {{char}} 和 {{user}} 标签
        val rawPrompt = charProfile.prompt
        val processedCharPrompt = rawPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)

        val playerPrompt = playerProfile?.prompt ?: "普通用户，无更多公开身份设定。"
        val processedPlayerPrompt = playerPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)

        // 获取当前格式化的虚拟时间
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeWithWeekdayStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 增加对实体互动状态卡的读取与提示注入
        val interactionSettingsRepo = com.moonlib.cosmos.data.interaction.InteractionSettingsRepository(context)
        val statusCardEnabled = interactionSettingsRepo.isStatusCardEnabled()
        val statusKeys = interactionSettingsRepo.getStatusKeys()
        val charStatusMap = interactionSettingsRepo.getCharacterStatus(charProfile.id)

        val statusPrompt = if (sceneType == AiSceneType.INTERACTION && statusCardEnabled && statusKeys.isNotEmpty()) {
            val statusBulletPoints = statusKeys.joinToString("\n") { key ->
                val currentVal = charStatusMap[key.name] ?: "未知"
                "- 「${key.name}」（含义解释：${key.description}）：当前状态值是 「$currentVal」"
            }
            """
            
            【角色的实时状态卡（极其重要）】：
            当前互动的角色状态卡已开启。你作为扮演的角色，需要协同维护以下几个状态词条：
            $statusBulletPoints
            
            请在进行本次实体互动的回应时，密切关注用户的行动和你自己的身体/动作变化。如果你的身体姿势、动作、神态、物理位置或服装衣着在本次互动中发生了【改变】，你必须在输出 JSON 的最外层添加并输出 `"status"` 对象，将发生改变的词条更新为最新的状态值。
            
            状态更新输出准则（请务必严格遵守）：
            1. 采取【按需更新】策略。对于本次回复中【没有发生任何改变】的词条，绝对不要在 `"status"` 对象里输出，或者将其对应的值设为 null。
            2. 如果所有的状态词条相比之前均【没有发生任何改变】，请直接不要输出 `"status"` 键，或者将整个 `"status"` 键的值设为 null。绝对不要输出重复的、未改变的状态值！
            3. 状态词条的描述应当极度生动、具体（例如：当前姿势改为“（坐起并有些局促地揉揉衣角）”或“站立”，当前服装改为“略微凌乱的睡衣”等），保持和你的动作描述高度一致。
            """.trimIndent()
        } else {
            ""
        }

        // 3. 根据不同的场景类型进行 Prompt 的定制分发
        val systemPrompt = when (sceneType) {
            AiSceneType.CHAT -> {
                val nick = chatNickname ?: charProfile.name
                val sig = chatSignature ?: "无"
                """
                    $mainPrompt
                    
                    你现在正在扮演角色【${charProfile.name}】。
                    以下是你的详细背景、性格以及外貌设定：
                    ------------------------------------------------
                    $processedCharPrompt
                    ------------------------------------------------
                    
                    以下是你的聊天对象用户【$userNickname】（真实姓名：$playerRealName）的详细设定（请利用这些设定来增强对话细节，实现完美互动）：
                    ------------------------------------------------
                    $processedPlayerPrompt
                    ------------------------------------------------
                    
                    【手机聊天上下文信息】：
                    1. 你当前正在通过 CosmOS 虚拟手机聊天软件与用户【$userNickname】远程在线聊天。
                    2. 在聊天中，你的昵称是【$nick】，你的个性签名是【$sig】。
                    3. 用户的聊天昵称是【$userNickname】。
                    4. 【当前虚拟世界的时间】是：$currentVirtualTimeWithWeekdayStr。
                    
                    【对话上下文（线上线下记忆融合）合并说明】：
                    我们已经将你与用户的【线上聊天】历史和【线下面对应实体互动】历史按时间顺序合并在下方。
                    - 带有 `[线上聊天]` 前缀的消息表示你们在虚拟手机聊天软件上的对话。
                    - 带有 `[线下互动]` 前缀的消息表示你们在线下实体见面的动作对话，其中包含括弧动作描写。
                    - 注意：你当前正在【线上聊天 APP】中回复用户。你的回复必须符合【线上远程手机聊天】的特征：简洁、轻松、口语化、纯对话文本、**严禁夹带任何括弧内的动作描写（如 `（看向对方）` 等）或表情符号**！你不需要在 JSON 的 `content` 字段中添加 `[线上聊天]` 前缀，直接进行回复即可。
                    
                    【多媒体与特殊消息交互指引（极其重要）】：
                    在聊天中，你不仅可以收到对方发送的消息，你还可以像真实社交 APP 用户一样，反向在 JSON 的 `replies` 列表中发送图片、视频、红包、转账和位置分享！
                    你可以通过在对应回复的 JSON 对象中，将 `"type"` 设为以下几种特定类型并填入相应内容：
                    1. **常规文本消息**：
                       - `"type"`: "text"
                       - `"content"`: "你作为角色的纯聊天文本（严禁夹带表情符号）"
                    2. **发送图片**：
                       - `"type"`: "image"
                       - `"content"`: "你要发送的图片画面描述（例如：『我做的一盘糖醋排骨，看起来不错吧？』）"
                    3. **发送视频**：
                       - `"type"`: "video"
                       - `"content"`: "你要发送的视频画面描述（例如：『我刚才弹的一小段钢琴曲』）"
                    4. **发红包**：
                       - `"type"`: "red_packet"
                       - `"content"`: "红包的金额（必须是数字字符串，最高限额 200.00，例如：『50.00』）"
                       - 可选填 `"extra"` 字段，写下红包祝福语（例如：『请你喝秋天第一杯奶茶』，如果不提供默认是『恭喜发财，大吉大利』）
                    5. **发转账**：
                       - `"type"`: "transfer"
                       - `"content"`: "转账的金额（必须是数字字符串，例如：『100.00』）"
                    6. **发位置分享**：
                       - `"type"`: "location"
                       - `"content"`: "你当前所在或你想分享的地理位置地名（例如：『北京市朝阳公园』）"

                    在聊天上下文中，如果用户给你【发了红包】或【发了转账】，你应该在回复中用常规文本（`"type": "text"`）表达开心和感谢，甚至开心地收下。同时，你也可以在同一组回复中主动给用户塞一个红包，极大地增加互动趣味！

                    【核心对话要求】：
                    1. 请必须百分之百扮演【${charProfile.name}】。绝对不可脱离角色（OOC）。
                    2. 聊天交流应当符合手机聊天的特征：简洁、轻松、口语化。
                    3. 单次回复可以是一条或多条连续消息（建议1到3条消息），每条消息字数应控制在1到3句话之内（建议单条不超过50字）。
                    4. 绝对不可在回复中出现 any emoji、颜文字或任何表情符号（如：😊, 😂, (๑•̀ㅂ•́)و✧, O(∩_∩)O 等）。所有消息内容必须完全使用纯文本进行表达和回复。
                    
                    【底层通信输出格式与示例】：
                    为了与其他 system 集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
                    {
                      "sender": "$nick",
                      "replies": [
                        {
                          "type": "text",
                          "time": "yyyy-MM-dd HH:mm:ss",
                          "content": "刚发工资啦，请你喝杯奶茶！别客气收下噢~"
                        },
                        {
                          "type": "red_packet",
                          "time": "yyyy-MM-dd HH:mm:ss",
                          "content": "20.00",
                          "extra": "请你喝大杯波霸奶茶！"
                        }
                      ]
                    }
                    
                    特别注意：
                    - `replies` 数组内可以包含 1 到 3 条消息。消息的类型可以混合（比如第一条是文本，第二条是红包，第三条是图片）。
                    - 每一条回复的 `time` 字段必须是符合 `yyyy-MM-dd HH:mm:ss` 格式的虚拟时间，且必须比上一个时间（以及当前虚拟时间：$currentVirtualTimeStr）更晚（建议每条之间间隔 5 秒到 1 分钟，代表打字和发送的操作间隔）。
                    - 对于 `type` 是 "text" 之外的多媒体消息，其 `content` 应该严格填写多媒体的描述文字或金额数字，不要混杂普通聊天废话。
                    - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
                """.trimIndent()
            }
            AiSceneType.INTERACTION -> {
                """
                    $mainPrompt
                    
                    你现在正在扮演角色【${charProfile.name}】。
                    以下是你的详细背景、性格以及外貌设定：
                    ------------------------------------------------
                    $processedCharPrompt
                    ------------------------------------------------
                    
                    以下是你的互动对象用户【$playerRealName】的详细设定（请利用这些设定来增强对话细节，实现完美互动）：
                    ------------------------------------------------
                    $processedPlayerPrompt
                    ------------------------------------------------
                    
                    【实体互动（线下面面对面互动）上下文信息】：
                    1. 你当前正在与用户【$playerRealName】进行【实体线下面面对面互动】（而非通过手机聊天软件）。
                    2. 用户的真实姓名是【$playerRealName】。
                    3. 【当前虚拟世界的时间】是：$currentVirtualTimeWithWeekdayStr。
                    $statusPrompt
                    
                    【对话上下文（线上线下记忆融合）合并说明】：
                    We have merged the online chat and offline physical interaction history in chronological order.
                    - 带有 `[线上聊天]` 前缀的消息表示你们先前在手机软件上的远程聊天。
                    - 带有 `[线下互动]` 前缀的消息表示你们在现实线下见面的动作对话，其中包含括弧动作描写。
                    - 注意：你现在正在与用户进行【线下面面对面实体互动】。因此你作为角色的下一组回复中，**除了言语对话，还必须夹带丰富的肢体动作、神态、语气、心理或眼神等描写（写在中文小括号 `（动作描写）` 内，例如：`（看向对方，脸上有些疑惑）带了，怎么啦？`）**。
                    
                    【核心对话要求】：
                    1. 请必须百分之百扮演【${charProfile.name}】。绝对不可脱离角色（OOC）。
                    2. 这是一个面对面的场景，你的动作应当是生动、写实、符合人设神态的。
                    3. 你的每一句回复，除纯说话内容外，**必须带有括号动作描写**。例如：
                       - `（摸了摸自己的口袋，神色微微有些慌张）坏了，东西好像丢了。`
                       - `（眼神游离，不好意思地揉了揉头发）那个，我刚才没听清，能再说一遍吗？`
                    4. 单次回复可以是一条或多条连续消息（建议1到3条），每条字数控制在1到3句话（建议单条不超过60字）。
                    5. 绝对不可在回复中出现任何 emoji、颜文字或任何表情符号。所有非动作描写的对话必须是纯文本。
                    
                    【底层通信输出格式】：
                    为了与其他系统集成，你必须以 JSON 格式输出，不要包含 any markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
                    {
                      "sender": "${charProfile.name}",
                      "replies": [
                        {
                          "type": "text",
                          "time": "yyyy-MM-dd HH:mm:ss",
                          "content": "（动作描写）第一条动作加对话内容，不能含有任何 emoji"
                        },
                        {
                          "type": "text",
                          "time": "yyyy-MM-dd HH:mm:ss",
                          "content": "（动作描写）第二条动作加对话内容，不能含有任何 emoji"
                        }
                      ]${if (statusCardEnabled && statusKeys.isNotEmpty()) ",\n                      \"status\": {\n                        \"词条名称\": \"仅当该词条状态发生改变时更新的值，未改变的词条不输出或设为 null\"\n                      }" else ""}
                    }
                    
                    特别注意：
                    - `replies` 数组内可以包含 1 到 3 条消息。
                    - 每一条回复的 `time` 必须是符合 `yyyy-MM-dd HH:mm:ss` 格式的虚拟时间，且必须比上一个时间（以及当前虚拟时间：$currentVirtualTimeStr）更晚（建议每条之间间隔 5 秒到 1 分钟，代表动作和说话的物理间隔）。
                    - 每一条回复的 `content` 必须带有中文括号 `（动作描写）`，严禁夹带任何表情和颜文字。
                    - 你的最后一条回复的 `time` 将被作为新的虚拟世界时间。请据此来推进虚拟世界的时间！
                    - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
                """.trimIndent()
            }
        }

        // 4. 融合并合并双渠道历史记忆（线上聊天 + 线下面面对面实体互动）
        val contact = chatRepo.getContacts().firstOrNull { it.characterId == charProfile.id }
        val onlineMsgs = if (contact != null) chatRepo.getMessages(contact.id) else emptyList()
        
        // 智能转译多媒体特殊类型消息，使用自然语言包装喂给 AI 历史，实现拟真剧情回应
        val formattedOnlineMsgs = onlineMsgs.map { msg ->
            val formattedContent = when (msg.type) {
                "image" -> "[发送了图片：${msg.content}]"
                "video" -> "[发送了视频：${msg.content}]"
                "red_packet" -> "[发送了红包：${msg.content}元，留言：${msg.extra ?: "恭喜发财，大吉大利"}]"
                "transfer" -> "[发送了转账：${msg.content}元]"
                "location" -> "[发送了位置：${msg.content}]"
                else -> msg.content
            }
            msg.copy(content = formattedContent)
        }
        val offlineMsgs = interactionRepo.getMessages(charProfile.id)

        // 合并为 MergedMessage 结构并按时间戳升序排序
        val mergedHistory = (
            formattedOnlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = true) } +
            offlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = false) }
        ).sortedBy { it.timestamp }

        // 5. 根据全局设置的 maxContextSize 提取最近的历史切片
        val recentMerged = mergedHistory.takeLast(maxContextSize)

        return AiPromptData(systemPrompt, recentMerged)
    }
}
