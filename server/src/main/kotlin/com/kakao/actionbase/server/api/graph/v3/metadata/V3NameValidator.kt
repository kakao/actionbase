package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.util.NameValidator

object V3NameValidator {
    fun validate(name: String, fieldName: String): String = NameValidator.validate(name, fieldName)
    fun validateDatabase(name: String): String = NameValidator.validate(name, "database")
    fun validateTable(name: String): String = NameValidator.validate(name, "table")
    fun validateAlias(name: String): String = NameValidator.validate(name, "alias")
}
