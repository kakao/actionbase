package com.kakao.actionbase.server.util

import com.kakao.actionbase.core.Constants
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object NameValidator {
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
}
