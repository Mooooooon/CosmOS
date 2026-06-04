package com.moonlib.cosmos.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedPacketMessageStateTest {

    @Test
    fun `mark received keeps original wish`() {
        val message = redPacket(extra = "天天开心")

        val received = message.markRedPacketReceived()

        assertEquals("天天开心", received.redPacketState().wish)
        assertTrue(received.redPacketState().isReceived)
    }

    @Test
    fun `legacy received marker uses default wish`() {
        val state = redPacket(extra = "received").redPacketState()

        assertEquals(DEFAULT_RED_PACKET_WISH, state.wish)
        assertTrue(state.isReceived)
    }

    @Test
    fun `unreceived packet keeps wish`() {
        val state = redPacket(extra = "平安喜乐").redPacketState()

        assertEquals("平安喜乐", state.wish)
        assertFalse(state.isReceived)
    }

    private fun redPacket(extra: String?) = ChatMessage(
        id = "message-id",
        senderId = "contact-id",
        content = "88.88",
        timestamp = 1L,
        type = "red_packet",
        extra = extra
    )
}
