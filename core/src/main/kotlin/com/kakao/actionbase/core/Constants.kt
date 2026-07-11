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

        // 8 KiB; stays below HBase MOB threshold (~100 KiB).
        const val DEFAULT_BUFFER_SIZE: Int = 8192

        const val BYTE_FALSE: Byte = 0
        const val BYTE_TRUE: Byte = 1
    }

    /**
     * Naming policy for all Actionbase metadata entities (database, table, alias, service, label, storage).
     * Rationale: URL path safety + HBase naming constraints both require lowercase alphanumeric and underscore only.
     */
    object Name {
        const val PATTERN = "^[a-z][a-z0-9_]{0,63}$"
        const val MESSAGE = "name must start with a lowercase letter and contain only lowercase alphanumeric characters or underscores (max 64 chars)"

        const val COMMENT_MAX_LENGTH = 1000
        const val COMMENT_SIZE_MESSAGE = "comment must be at most $COMMENT_MAX_LENGTH characters"

        const val STORAGE_URI_PATTERN = "^datastore://[a-z][a-z0-9_]*/[a-z][a-z0-9_]*$"
        const val STORAGE_URI_MESSAGE = "storage must be in format datastore://<namespace>/<table> where each segment starts with a lowercase letter and contains only lowercase alphanumeric/underscore (e.g., datastore://my_namespace/my_table)"
    }

    object Group {
        const val DEFAULT_TTL = 8 * 24 * 60 * 60 * 1000L // 8 days in milliseconds (691,200,000)

        /** Sentinel group name that delegates to the native edge-count query. */
        const val COUNT_SENTINEL = "__count__"
    }
}
