package com.kakao.actionbase.engine

import com.kakao.actionbase.core.metadata.common.Group

data class QualifiedGroups(
    val database: String,
    val table: String,
    val groups: List<Group>,
)
