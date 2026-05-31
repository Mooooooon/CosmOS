package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.settings.AiSceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 聊天联系人展示资料生成器。
 *
 * 职责单一：根据已关联的人设档案，生成联系人昵称与个性签名。
 */
object ContactMetadataGenerator {

    data class Result(
        val nickname: String,
        val signature: String
    )

    suspend fun generate(
        context: Context,
        profile: CharacterProfile
    ): Result = withContext(Dispatchers.IO) {
        if (profile.prompt.isBlank()) {
            throw Exception("所选人设档案内容为空，无法生成联系人资料。")
        }

        val response = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.CONTACT_PROFILE_GENERATION,
                systemPrompt = SYSTEM_PROMPT,
                personaPrompt = buildPersonaPrompt(profile),
                outputRequirement = "只返回 JSON 对象，不要解释，不要使用 Markdown。nickname 必须是网名 / 社交账号名风格，禁止直接使用人物姓名、姓名简称、亲昵称呼或备注名；signature 必须代入角色本人，像她会在聊天 APP 中写下的自我表达，不要写成第三人称简介。",
                jsonStructure = """{"nickname":"联系人昵称","signature":"个性签名"}""",
                userInput = "请根据已关联的人设档案，生成适合聊天联系人列表展示的昵称和个性签名。",
                logCharacterName = "联系人资料生成器",
                logUserInput = profile.name,
                responseSchema = AiJsonSchemaFactory.contactMetadataSchema(),
                expectsJson = true
            )
        )

        parseResult(response.cleanedJson)
    }

    private fun buildPersonaPrompt(profile: CharacterProfile): String {
        return """
            【关联人设档案】
            档案姓名：${profile.name}
            档案内容：
            ${profile.prompt.trim()}
        """.trimIndent()
    }

    private fun parseResult(cleanedJson: String): Result {
        val json = JSONObject(cleanedJson)
        val nickname = json.optString("nickname").trim()
        val signature = json.optString("signature").trim()
        if (nickname.isBlank()) {
            throw Exception("AI 未返回有效昵称，请稍后重试。")
        }
        return Result(
            nickname = nickname.take(24),
            signature = signature.take(80)
        )
    }

    private const val SYSTEM_PROMPT = """
        你是聊天应用的联系人资料设计助手。
        你的任务是根据角色人设，为该角色生成一个聊天联系人昵称和一句个性签名。
        昵称必须是网名 / 社交账号名风格，例如像用户自己取的网络 ID，而不是联系人备注。
        严禁直接使用档案姓名、真实姓名、人物简称、姓名谐音、亲昵称呼或“阿X / 小X / XX哥 / XX姐”等称呼式昵称。
        昵称应自然、简洁、符合角色气质，可以体现角色意象、兴趣、身份、口吻或生活状态。
        个性签名必须代入角色本人，想象她会在聊天 APP 中用什么签名表达自己。
        签名应使用第一人称或含蓄的自我表达，体现角色口吻、生活状态、情绪底色或性格特征。
        严禁写成第三人称介绍、角色简介、设定摘要或旁白解说。
        不要输出 emoji、颜文字、Markdown 或多余解释。
    """
}
