package com.kakao.actionbase.engine.service

internal fun parseFqn(fqn: String): Pair<String, String> {
    val dot = fqn.indexOf('.')
    require(dot > 0 && dot < fqn.lastIndex) {
        "table must be a fully-qualified `database.table`, got: $fqn"
    }
    return fqn.substring(0, dot) to fqn.substring(dot + 1)
}
