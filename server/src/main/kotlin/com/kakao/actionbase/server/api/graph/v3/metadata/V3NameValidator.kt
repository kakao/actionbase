package com.kakao.actionbase.server.api.graph.v3.metadata

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Validates names for V3 Metadata API to prevent injection attacks.
 * Names must start with a letter and contain only alphanumeric characters,
 * underscores, or hyphens. Maximum length is 64 characters.
 */
object V3NameValidator {
    private val NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$")

    /**
     * Validates a name and returns it if valid.
     * @throws ResponseStatusException with 400 Bad Request if invalid
     */
    fun validate(
        name: String,
        fieldName: String,
    ): String {
        if (!name.matches(NAME_PATTERN)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid $fieldName: must start with a letter, contain only alphanumeric/underscore/hyphen, max 64 chars",
            )
        }
        return name
    }

    fun validateDatabase(name: String): String = validate(name, "database")

    fun validateTable(name: String): String = validate(name, "table")

    fun validateAlias(name: String): String = validate(name, "alias")
}
