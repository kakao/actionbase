package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.engine.Engine

interface CatalogLoader : AutoCloseable {
    fun bind(engine: Engine)
}
