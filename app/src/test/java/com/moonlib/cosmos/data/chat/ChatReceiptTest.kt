package com.moonlib.cosmos.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReceiptTest {

    @Test
    fun `receipt references source message and marks it received`() {
        val source = message(id = "packet", senderId = "contact", type = TYPE_RED_PACKET)
        val messages = listOf(source)

        val receipt = messages.createReceipt(source, receiverId = "user", timestamp = 2L)!!

        assertEquals(TYPE_RED_PACKET_RECEIPT, receipt.type)
        assertEquals("packet", receipt.extra)
        assertTrue((messages + receipt).hasReceiptFor("packet"))
    }

    @Test
    fun `sender cannot receive own message`() {
        val source = message(id = "transfer", senderId = "user", type = TYPE_TRANSFER)

        assertNull(listOf(source).createReceipt(source, receiverId = "user", timestamp = 2L))
    }

    @Test
    fun `message can only be received once`() {
        val source = message(id = "packet", senderId = "contact", type = TYPE_RED_PACKET)
        val receipt = listOf(source).createReceipt(source, receiverId = "user", timestamp = 2L)!!

        assertNull(listOf(source, receipt).createReceipt(source, receiverId = "user", timestamp = 3L))
    }

    @Test
    fun `latest receipt selects newest unclaimed incoming message`() {
        val older = message(id = "older", senderId = "user", type = TYPE_TRANSFER, timestamp = 1L)
        val newer = message(id = "newer", senderId = "user", type = TYPE_TRANSFER, timestamp = 2L)

        val receipt = listOf(older, newer).createLatestReceipt(TYPE_TRANSFER, "contact", 3L)

        assertEquals("newer", receipt?.extra)
    }

    private fun message(
        id: String,
        senderId: String,
        type: String,
        timestamp: Long = 1L
    ) = ChatMessage(
        id = id,
        senderId = senderId,
        content = "88.88",
        timestamp = timestamp,
        type = type
    )
}
