package com.moonlib.cosmos.data.context

import com.moonlib.cosmos.data.interaction.InteractionMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionContextHistoryCollectorTest {

    @Test
    fun collectForCharactersAddsSharedMessagesFromOtherParticipants() {
        val messagesByCharacterId = mapOf(
            "a" to listOf(
                message(id = "single-a", senderId = "a", timestamp = 1L, participantIds = emptyList()),
                message(id = "reply-a", senderId = "a", timestamp = 2L, participantIds = listOf("a", "b"))
            ),
            "b" to listOf(
                message(id = "reply-b", senderId = "b", timestamp = 3L, participantIds = listOf("a", "b"))
            ),
            "c" to listOf(
                message(id = "single-c", senderId = "c", timestamp = 4L, participantIds = emptyList())
            )
        )

        val result = InteractionContextHistoryCollector.collectForCharacters(
            targetCharacterIds = setOf("a"),
            allCharacterIds = setOf("a", "b", "c"),
            getMessages = { messagesByCharacterId[it].orEmpty() }
        )

        assertEquals(listOf("single-a", "reply-a", "reply-b"), result.map { it.id })
    }

    private fun message(
        id: String,
        senderId: String,
        timestamp: Long,
        participantIds: List<String>
    ): InteractionMessage {
        return InteractionMessage(
            id = id,
            senderId = senderId,
            content = "content",
            timestamp = timestamp,
            participantIds = participantIds
        )
    }
}
