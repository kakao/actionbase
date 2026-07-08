package com.kakao.actionbase.core

object Constants {
    const val VERTEX_MARKER: String = "-"

    const val VERTEX_TARGET_COMMENT: String = "<vertex>"

    const val EDGE_TABLE_NAME: String = "edges"

    const val LOCK_TABLE_NAME: String = "locks"

    val DEFAULT_COLUMN_FAMILY: ByteArray = "f".toByteArray()

    val DEFAULT_QUALIFIER: ByteArray = "e".toByteArray()

    const val DEFAULT_COMMENT: String = ""

    const val DEFAULT_CREATED_AT: Long = -1L

    const val DEFAULT_CREATED_BY: String = ""

    const val DEFAULT_UPDATED_AT: Long = -1L

    const val DEFAULT_UPDATED_BY: String = ""

    const val DEFAULT_REVISION: Long = -1L

    object Codec {
        const val DEFAULT_POOL_SIZE: Int = 100
        const val DEFAULT_BUFFER_SIZE: Int = 5120

        const val BYTE_FALSE: Byte = 0
        const val BYTE_TRUE: Byte = 1
    }

    object Group {
        const val DEFAULT_TTL = 8 * 24 * 60 * 60 * 1000L // 8 days in milliseconds (691,200,000)

        /**
         * Group names reserved for special handling by the engine. An entry here is
         * the single source of truth: the read (AGG) and mutation paths both derive
         * their sentinel checks from this enum, so adding a new reserved name never
         * requires touching more than one place.
         */
        enum class Reserved(val groupName: String) {
            /** Delegates to the native edge-count query instead of a real EdgeGroup lookup. */
            COUNT_SENTINEL("__count__"),
            ;

            companion object {
                private val names = entries.map { it.groupName }.toSet()

                fun contains(groupName: String): Boolean = groupName in names
            }
        }
    }
}
