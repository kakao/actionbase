package com.kakao.actionbase.core.edge.record

import com.kakao.actionbase.core.state.EncodedModelType

enum class EdgeRecordType(
    override val code: Byte,
) : EncodedModelType {
    EDGE_LOCK(-1),
    EDGE_COUNT(-2),
    EDGE_STATE(-3),
    EDGE_INDEX(-4),
    EDGE_GROUP(-5),
    EDGE_CACHE(-6),
    ;

    companion object {
        const val INVALID_RECORD_TYPE_CODE: Byte = Byte.MIN_VALUE

        private val ENCODED_EDGE_TYPE_BY_CODE = entries.associateBy(EncodedModelType::code)

        fun of(code: Byte): EdgeRecordType = ENCODED_EDGE_TYPE_BY_CODE[code] ?: throw IllegalArgumentException("Invalid encoded edge type code: $code")
    }
}
