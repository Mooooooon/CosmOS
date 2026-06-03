package com.moonlib.cosmos.data.context

import com.moonlib.cosmos.data.interaction.InteractionMessage
import com.moonlib.cosmos.data.interaction.InteractionRepository

/**
 * 互动上下文历史收集器。
 *
 * 职责单一：为目标角色补齐其参与过的单人互动与多人共同互动记录。
 */
object InteractionContextHistoryCollector {

    fun collectForCharacters(
        interactionRepo: InteractionRepository,
        targetCharacterIds: Set<String>,
        allCharacterIds: Set<String>
    ): List<InteractionMessage> {
        return collectForCharacters(
            targetCharacterIds = targetCharacterIds,
            allCharacterIds = allCharacterIds,
            getMessages = interactionRepo::getMessages
        )
    }

    fun collectForCharacters(
        targetCharacterIds: Set<String>,
        allCharacterIds: Set<String>,
        getMessages: (String) -> List<InteractionMessage>
    ): List<InteractionMessage> {
        if (targetCharacterIds.isEmpty()) return emptyList()

        val directMessages = targetCharacterIds.flatMap(getMessages)
        val sharedMessages = allCharacterIds
            .flatMap(getMessages)
            .filter { message -> message.isSharedWithAny(targetCharacterIds) }

        return mergeDistinctById(directMessages + sharedMessages)
    }

    fun mergeDistinctById(messages: List<InteractionMessage>): List<InteractionMessage> {
        return messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    private fun InteractionMessage.isSharedWithAny(targetCharacterIds: Set<String>): Boolean {
        return participantIds.any { it in targetCharacterIds }
    }
}
