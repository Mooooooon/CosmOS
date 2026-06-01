package com.moonlib.cosmos.data.profile

import android.content.Context
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 角色取名生成助手
 *
 * 职责单一：负责结合当前激活的 AI 模型，根据人设提示词和上下文，异步生成 10 个高度匹配、富有深度和代入感的备选姓名。
 */
object CharacterNameGenerator {

    private val SYSTEM_PROMPT = """
        你是一个专业的小说作家与剧情策划。
        请根据用户提供的人设提示词，精心为该角色（或用户自己）设计 10 个高度契合、富有代入感、充满故事感和性格特征的备选姓名。
        
        【设计原则】：
        1. 名字应符合角色的人设背景、性格、身份和世界观。
        2. 名字要好听、朗朗上口，并且包含鲜明的特色（比如：东方仙侠风、现代科幻感、古风典雅、西方魔幻等，根据人设自动匹配最佳风格）。
        3. 请提供 10 个名字，并返回为一个 JSON 数组。
        
        【重要约束】：
        严禁携带任何废话、JSON包裹之外的前言、后语或解释说明，直接输出 JSON。
    """.trimIndent()

    suspend fun generateNames(
        context: Context,
        prompt: String
    ): List<String> = withContext(Dispatchers.IO) {
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("请先前往【系统设置 -> AI模型服务配置】中添加并激活至少一个 AI 模型服务商。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查配置。")
        }

        val schema = AiJsonSchemaFactory.characterNamesSchema()

        val response = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.CHARACTER_NAME_GENERATION,
                systemPrompt = SYSTEM_PROMPT,
                worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                personaPrompt = "人设提示词如下：\n$prompt",
                outputRequirement = "必须完全按照指定的 JSON 结构返回包含 10 个名字的数组，不要解释，不要使用 Markdown 包裹，仅输出合法 JSON 对象。",
                jsonStructure = """
                    {
                      "names": ["名字1", "名字2", "名字3", "名字4", "名字5", "名字6", "名字7", "名字8", "名字9", "名字10"]
                    }
                """.trimIndent(),
                userInput = "请根据上述人设，为我构思 10 个高度契合且有代入感的名字。",
                logCharacterName = "取名生成器",
                logUserInput = "为人设生成10个备选名字",
                responseSchema = schema,
                expectsJson = true
            )
        )

        val jsonStr = response.cleanedJson
        try {
            val jsonObject = JSONObject(jsonStr)
            val jsonArray = jsonObject.getJSONArray("names")
            val nameList = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val name = jsonArray.getString(i).trim()
                if (name.isNotBlank()) {
                    nameList.add(name)
                }
            }
            if (nameList.isEmpty()) {
                throw Exception("AI 返回的名字列表为空")
            }
            nameList
        } catch (e: Exception) {
            throw Exception("解析 AI 返回的名字数据失败：${e.message}\n原始响应：${response.rawResponse}")
        }
    }
}
