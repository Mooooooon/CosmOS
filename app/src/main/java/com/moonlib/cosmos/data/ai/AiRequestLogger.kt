package com.moonlib.cosmos.data.ai

import android.content.Context
import com.moonlib.cosmos.data.settings.AiLogRepository

/**
 * AI 通讯日志记录器。
 */
object AiRequestLogger {

    fun save(
        context: Context,
        characterName: String,
        modelName: String,
        userInput: String,
        aiResponse: String,
        prompt: String
    ) {
        try {
            AiLogRepository(context).saveLog(
                characterName = characterName,
                modelName = modelName,
                userInput = userInput,
                aiResponse = aiResponse,
                prompt = prompt
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
