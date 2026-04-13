package com.kakao.actionbase.engine.runtime

interface CatalogLoader : AutoCloseable {
    fun bind(engine: Engine)
}
