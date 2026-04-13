package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.engine.Engine

interface Catalog : AutoCloseable {
    fun bind(engine: Engine)
}
