package com.kakao.actionbase.engine.runtime

interface MetadataLoader : AutoCloseable {
    fun bind(engine: Engine)
}
