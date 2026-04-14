package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.DatabaseId
import com.kakao.actionbase.core.metadata.TableId
import com.kakao.actionbase.engine.Engine

interface Catalog : AutoCloseable {
    fun bind(engine: Engine)

    val databases: Map<DatabaseId, Database>

    val tables: Map<TableId, Table>

    val aliases: Map<TableId, Alias>
}
