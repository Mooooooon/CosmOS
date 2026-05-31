package com.moonlib.cosmos.data.profile

/**
 * 关键词驱动的角色档案匹配工具
 *
 * 职责单一：根据用户近期输入文本，从候选角色档案中检测关键词命中情况，
 * 返回被提及的角色列表，供 Engine 层将其人设追加到 personaPrompt。
 */
object KeywordProfileMatcher {

    /**
     * 从 [recentUserInputs] 的最近 [recentCount] 条文本中，
     * 检测 [candidateProfiles] 中哪些角色的关键词被提及。
     *
     * @param recentUserInputs 用户输入文本列表（按时间顺序，取最后 recentCount 条）
     * @param candidateProfiles 待检测的非主角角色列表（已排除当前场景主角和 player）
     * @param recentCount 取最近几条输入进行检测，默认 5
     * @return 命中的角色列表（保留原始顺序，去重）
     */
    fun match(
        recentUserInputs: List<String>,
        candidateProfiles: List<CharacterProfile>,
        recentCount: Int = 5
    ): List<CharacterProfile> {
        if (recentUserInputs.isEmpty() || candidateProfiles.isEmpty()) return emptyList()

        // 只取最近 recentCount 条，合并为一段待检测文本（忽略大小写）
        val combinedText = recentUserInputs.takeLast(recentCount).joinToString(" ").lowercase()

        return candidateProfiles.filter { profile ->
            // 只有填写了 keywords 的角色才参与匹配（避免所有角色姓名都被当隐式关键词产生误报）
            // 角色姓名本身也作为额外隐式关键词，确保填了 keywords 的角色可以被姓名直接命中
            if (profile.keywords.isEmpty()) return@filter false
            val effectiveKeywords = (profile.keywords + profile.name).filter { it.isNotBlank() }
            effectiveKeywords.any { keyword ->
                combinedText.contains(keyword.trim().lowercase())
            }
        }
    }

    /**
     * 将命中角色的人设组装为追加到 personaPrompt 末尾的说明段落。
     *
     * @param matchedProfiles 命中的角色列表
     * @param playerRealName 玩家真实姓名，用于替换 {{user}} 占位符
     * @return 格式化的人设补充文本，若无命中则返回空字符串
     */
    fun buildAppendedPersonaText(
        matchedProfiles: List<CharacterProfile>,
        playerRealName: String
    ): String {
        if (matchedProfiles.isEmpty()) return ""
        return buildString {
            append("\n\n【场景中被提及的其他相关角色（仅供参考，不必主动扮演）】\n")
            matchedProfiles.forEach { profile ->
                val processedPrompt = profile.prompt
                    .replace("{{char}}", profile.name)
                    .replace("{{user}}", playerRealName)
                append("角色【${profile.name}】的性格设定：\n")
                append(processedPrompt)
                append("\n")
            }
        }
    }
}
