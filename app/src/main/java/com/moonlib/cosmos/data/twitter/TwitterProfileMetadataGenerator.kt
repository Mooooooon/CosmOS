package com.moonlib.cosmos.data.twitter

import android.content.Context
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.settings.AiSceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

import com.moonlib.cosmos.data.settings.SystemPromptRepository

/**
 * 推特博主主页资料生成器。
 *
 * 职责单一：根据角色人设生成面向公开网友的昵称、用户名与个人简介。
 */
object TwitterProfileMetadataGenerator {

    data class Result(
        val nickname: String,
        val username: String,
        val bio: String
    )

    suspend fun generate(
        context: Context,
        characterProfile: CharacterProfile?
    ): Result = withContext(Dispatchers.IO) {
        if (characterProfile == null || characterProfile.prompt.isBlank()) {
            throw Exception("未找到可参考的人设档案，无法生成推特主页资料。")
        }

        val response = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.TWITTER_PROFILE_GENERATION,
                systemPrompt = SYSTEM_PROMPT,
                worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                personaPrompt = buildPersonaPrompt(characterProfile),
                outputRequirement = "只返回 JSON 对象，不要解释，不要使用 Markdown。nickname 参考真实推特、微博等公开社交平台的昵称风格；username 不带 @，只能由英文字母和数字组成；bio 是面向全世界网友的主页简介，说明兴趣、常发内容或账号气质。",
                jsonStructure = """{"nickname":"公开社交昵称","username":"lettersAndNumbersOnly","bio":"个人简介"}""",
                userInput = "请根据角色人设生成推特博主资料：昵称、用户名和个人简介。这个账号面向全世界网友，不是亲友聊天备注。",
                logCharacterName = "推特资料生成器",
                logUserInput = characterProfile.name,
                responseSchema = AiJsonSchemaFactory.twitterProfileMetadataSchema(),
                expectsJson = true
            )
        )

        parseResult(response.cleanedJson)
    }

    private fun buildPersonaPrompt(characterProfile: CharacterProfile): String {
        return """
            【关联角色人设】
            角色姓名：${characterProfile.name}
            人设内容：
            ${characterProfile.prompt.trim()}
        """.trimIndent()
    }

    private fun parseResult(cleanedJson: String): Result {
        val json = JSONObject(cleanedJson)
        val nickname = json.optString("nickname").trim()
        val username = json.optString("username").toUsername()
        val bio = json.optString("bio").trim()
        if (nickname.isBlank() || username.isBlank()) {
            throw Exception("AI 未返回有效昵称或用户名，请稍后重试。")
        }
        return Result(
            nickname = nickname.take(32),
            username = username.take(20),
            bio = bio.take(120)
        )
    }

    private fun String.toUsername(): String {
        return trim()
            .removePrefix("@")
            .filter { it.isLetterOrDigit() }
            .lowercase()
    }

    private const val SYSTEM_PROMPT = """
        你是公开社交平台账号资料设计助手。
        你的任务是根据角色人设，为角色设计一个像真实推特、微博、小红书等公开社交平台会使用的账号资料。
        推特面向全世界网友，不是亲朋好友聊天软件，也不是联系人备注。

        nickname 是公开展示昵称，应参考真实推特、微博等平台风格，可以是网名、昵称、风格化短语、圈层身份或内容主题名；不要写成亲友备注。
        username 是 @ 后面的用户名，不要包含 @，只能使用英文字母和数字；它应在一定程度上体现用户个性，可以使用特殊单词、英文名、名字缩写、拼音、罗马音、兴趣词或年份数字。
        bio 是主页个人简介，应像本人写给全世界网友看的账号说明，介绍爱好、常发内容或账号方向，例如穿搭、自拍、游戏、读书、日常、摄影、音乐、吐槽、创作等。
        bio 不能写成第三人称角色介绍、设定摘要或旁白说明。

        不要输出 emoji、颜文字、Markdown 或多余解释。
    """
}
