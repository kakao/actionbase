package com.kakao.actionbase.v2.engine.metadata

enum class StorageType {
    NIL,
    LOCAL,
    JDBC,
    HBASE,
    SLATEDB,

    DATASTORE,
    ;

    companion object {
        private val NAME_TO_VALUE_MAP: Map<String, StorageType> = values().associateBy { it.name }

        fun of(name: String): StorageType? = NAME_TO_VALUE_MAP[name]
    }
}
