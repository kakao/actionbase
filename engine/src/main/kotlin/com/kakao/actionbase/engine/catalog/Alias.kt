package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.AliasDescriptor

interface Alias {
    val descriptor: AliasDescriptor

    /** The table this alias resolves to, already materialized. */
    val table: Table
}
