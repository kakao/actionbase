package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.DatabaseId
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.TableId
import com.kakao.actionbase.engine.Engine

interface Catalog : AutoCloseable {
    fun bind(engine: Engine)

    val databases: Map<DatabaseId, DatabaseDescriptor>

    val tables: Map<TableId, TableDescriptor<*>>

    val aliases: Map<TableId, AliasDescriptor>
}
