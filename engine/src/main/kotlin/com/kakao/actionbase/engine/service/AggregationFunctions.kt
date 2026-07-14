package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema

internal fun ModelSchema.groupsOrNull(): List<Group>? =
    when (this) {
        is ModelSchema.Edge -> groups
        is ModelSchema.MultiEdge -> groups
        else -> null
    }

internal fun parseFqn(fqn: String): Pair<String, String> {
    val dot = fqn.indexOf('.')
    require(dot > 0 && dot < fqn.lastIndex) {
        "table must be a fully-qualified `database.table`, got: $fqn"
    }
    return fqn.substring(0, dot) to fqn.substring(dot + 1)
}
