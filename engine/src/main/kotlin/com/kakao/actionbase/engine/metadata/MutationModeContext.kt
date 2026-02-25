package com.kakao.actionbase.engine.metadata

import com.kakao.actionbase.engine.metadata.MutationMode.ASYNC
import com.kakao.actionbase.engine.metadata.MutationMode.IGNORE
import com.kakao.actionbase.engine.metadata.MutationMode.SYNC

data class MutationModeContext(
    val label: MutationMode,
    val request: MutationMode?,
    val queue: Boolean,
) {
    companion object {
        @Suppress("CyclomaticComplexMethod")
        fun of(
            label: MutationMode,
            request: MutationMode?,
        ): MutationModeContext {
            val queue =
                when (label) {
                    ASYNC ->
                        when (request) {
                            null -> true
                            SYNC -> false
                            ASYNC -> true
                            IGNORE -> true
                        }

                    SYNC ->
                        when (request) {
                            null -> false
                            SYNC -> false
                            ASYNC -> true
                            IGNORE -> true
                        }

                    IGNORE ->
                        when (request) {
                            null -> true
                            SYNC -> throw IllegalArgumentException("SYNC is not allowed in IGNORE mode.")
                            ASYNC -> true
                            IGNORE -> true
                        }
                }
            return MutationModeContext(label, request, queue)
        }
    }
}
