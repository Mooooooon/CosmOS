package com.moonlib.cosmos.data.chat

const val DEFAULT_RED_PACKET_WISH = "恭喜发财，大吉大利"

private const val LEGACY_RECEIVED_MARKER = "received"

data class RedPacketMessageState(
    val wish: String,
    val isReceived: Boolean
)

fun ChatMessage.redPacketState(): RedPacketMessageState {
    val isLegacyReceived = extra == LEGACY_RECEIVED_MARKER
    return RedPacketMessageState(
        wish = extra
            ?.takeUnless { isLegacyReceived }
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RED_PACKET_WISH,
        isReceived = isRedPacketReceived || isLegacyReceived
    )
}

fun ChatMessage.markRedPacketReceived(): ChatMessage = copy(isRedPacketReceived = true)
