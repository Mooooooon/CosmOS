package com.moonlib.cosmos.data.profile

import android.content.Context
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.settings.AiServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 异步人设提示词生成助手
 * 
 * 职责单一：负责结合当前激活的 AI 模型，根据用户想法异步生成高保真 Markdown 人设配置。
 */
object CharacterProfileGenerator {

    /**
     * 系统指令：强行约束 AI 生成特定的结构，并强制执行 {{char}} 替代人名
     */
    private val SYSTEM_PROMPT = """
        你是一个专业的小说与角色扮演游戏人设设计师。
        请根据用户提供的人设核心想法，为角色或用户精心设计出富有深度、细节完整的人物设定。
        你必须完全遵守并按照以下指定的 Markdown 模板返回数据，严禁携带任何废话、Markdown包裹之外的前言、后语或解释说明，直接输出Markdown文本：
        
        # {{char}}
        ## 基本信息
        
        ## 身份背景
        
        ## 外貌身材设定
        
        ## 性格设定
        
        ## 穿衣风格
        1. A场合
        2. B场合
        
        ## 人际关系
        角色1
        角色2
        
        【重要核心规则】：
        1. 必须完全使用上面指定的二级标题模板，绝对不要自作聪明去增减、更改任何一级和二级标题！
        2. 在生成的内容中，凡是需要提到或出现该角色（设定主体）姓名的地方，你必须百分之百使用变量“{{char}}”代替。
        3. 在生成的内容中，凡是需要提到或出现“我”或用户/玩家姓名的地方，你必须百分之百使用变量“{{user}}”代替。
        4. 绝对不要在任何地方直接输出真实的姓名，确保角色扮演机制能完美动态匹配！
    """.trimIndent()

    /**
     * 发起网络请求，异步生成人设
     */
    suspend fun generateProfile(
        context: Context,
        userIdea: String
    ): String = withContext(Dispatchers.IO) {
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("请先前往【系统设置 -> AI模型服务配置】中添加并激活至少一个 AI 模型服务商。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查配置。")
        }

        // 识别是否为 Gemini 官方 API
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")

        if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, userIdea)
        } else {
            executeOpenAISync(baseUrl, modelName, apiKey, temperature, userIdea)
        }
    }

    /**
     * 针对官方 Gemini API generateContent 格式的适配
     */
    private fun executeGeminiOfficial(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        userIdea: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        // Gemini 官方 generateContent 接口地址
        val urlStr = "$base/v1beta/models/$modelName:generateContent?key=$apiKey"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        // 拼接 Prompt：由于官方 Gemini 结构不同，将 system instruction 拼在 prompt 头
        val fullPrompt = "$SYSTEM_PROMPT\n\n用户的核心想法是：$userIdea\n\n请直接开始生成，并全部使用 {{char}} 代替名字："
        
        // 构造 JSON 请求体
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply {
                            put("text", fullPrompt)
                        }
                    ))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
            })
        }

        // 写入请求数据
        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text").trim()
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            throw Exception("AI生成接口报错 HTTP $responseCode: ${errorText.take(150)}")
        }
    }

    /**
     * 针对标准 OpenAI/DeepSeek / 兼容 completions 格式的适配
     */
    private fun executeOpenAISync(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        userIdea: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        // 构造标准的 messages 数组，支持 system 角色
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "我的核心想法是：$userIdea。请完全按照指定的格式生成它，并全程用 {{char}} 代替角色人名。")
            })
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
        }

        // 写入数据
        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content").trim()
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            throw Exception("AI生成接口报错 HTTP $responseCode: ${errorText.take(150)}")
        }
    }
}
