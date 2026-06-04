package com.moonlib.cosmos.data.chat

import java.util.UUID

const val DEFAULT_RED_PACKET_WISH = "恭喜发财，大吉大利"
const val TYPE_RED_PACKET = "red_packet"
const val TYPE_TRANSFER = "transfer"
const val TYPE_RED_PACKET_RECEIPT = "red_packet_receipt"
const val TYPE_TRANSFER_RECEIPT = "transfer_receipt"

fun ChatMessage.redPacketWish(): String = extra?.takeIf { it.isNotBlank() } ?: DEFAULT_RED_PACKET_WISH

fun ChatMessage.isReceipt(): Boolean {
    return type == TYPE_RED_PACKET_RECEIPT || type == TYPE_TRANSFER_RECEIPT
}

fun List<ChatMessage>.hasReceiptFor(messageId: String): Boolean {
    return any { it.isReceipt() && it.extra == messageId }
}

fun List<ChatMessage>.createReceipt(
    sourceMessage: ChatMessage,
    receiverId: String,
    timestamp: Long
): ChatMessage? {
    if (sourceMessage.senderId == receiverId || hasReceiptFor(sourceMessage.id)) return null
    val receiptType = when (sourceMessage.type) {
        TYPE_RED_PACKET -> TYPE_RED_PACKET_RECEIPT
        TYPE_TRANSFER -> TYPE_TRANSFER_RECEIPT
        else -> return null
    }
    return ChatMessage(
        id = UUID.randomUUID().toString(),
        senderId = receiverId,
        content = sourceMessage.content,
        timestamp = timestamp,
        type = receiptType,
        extra = sourceMessage.id
    )
}

fun List<ChatMessage>.createLatestReceipt(
    sourceType: String,
    receiverId: String,
    timestamp: Long
): ChatMessage? {
    val source = asReversed().firstOrNull { message ->
        message.type == sourceType &&
            message.senderId != receiverId &&
            !hasReceiptFor(message.id)
    } ?: return null
    return createReceipt(source, receiverId, timestamp)
}
