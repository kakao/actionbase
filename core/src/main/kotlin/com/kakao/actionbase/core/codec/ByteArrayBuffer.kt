package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.codec.ValueUtils.serialize
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.java.codec.common.hbase.SimplePositionedMutableByteRange

typealias ByteArrayBuffer = SimplePositionedMutableByteRange

fun ByteArrayBuffer.putValue(
    value: Any?,
    order: Order = Order.ASC,
): ByteArrayBuffer {
    serialize(buffer = this, value, order)
    return this
}

fun <T> ByteArrayBuffer.getValue(): T = getValueOrNull() ?: throw IllegalStateException("value must not be null.")

fun <T> ByteArrayBuffer.getValueOrNull(): T? = ValueUtils.deserialize(buffer = this)

fun ByteArrayBuffer.hasRemaining(): Boolean = remaining > 0

fun ByteArrayBuffer.plusOne(): ByteArrayBuffer {
    val position = this.position
    val bytes = this.bytes
    var carry = true
    var i = position - 1
    while (i >= 0 && carry) {
        if ((bytes[i].toInt() and 0xFF) == 0xFF) {
            bytes[i] = 0
        } else {
            bytes[i]++
            carry = false
        }
        i--
    }
    require(!carry) { "Overflow while incrementing byte buffer" }
    return this
}

fun ByteArray.buffer(): ByteArrayBuffer = ByteArrayBuffer(this)
