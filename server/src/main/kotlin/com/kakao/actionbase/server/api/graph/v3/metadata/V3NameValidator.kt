package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object V3NameValidator {
    private val NAME_PATTERN = Regex(Constants.Name.PATTERN)

    fun validate(
        name: String,
        fieldName: String,
    ): String {
        if (!name.matches(NAME_PATTERN)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid $fieldName: ${Constants.Name.MESSAGE}",
            )
        }
        return name
    }

    fun validateDatabase(name: String): String = validate(name, "database")

    fun validateTable(name: String): String = validate(name, "table")

    fun validateAlias(name: String): String = validate(name, "alias")
}
