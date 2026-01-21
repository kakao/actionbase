package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.MutationMode.ASYNC
import com.kakao.actionbase.v2.core.metadata.MutationMode.IGNORE
import com.kakao.actionbase.v2.core.metadata.MutationMode.SYNC

data class MutationModeContext(
    val l: MutationMode, // label (table)
    val g: MutationMode?, // global
    val r: MutationMode?, // request
    val queue: Boolean,
) {
    companion object {
        /**
         * Priority: r(equest) > g(lobal) > t(able)
         *
         * | t(able) | g(lobal) | r(equest) | queue |
         * | ------- | -------- | --------- | ----- |
         * | ASYNC   | N/A      | N/A       | true  |
         * | ASYNC   | N/A      | SYNC      | false |
         * | ASYNC   | N/A      | ASYNC     | true  |
         * | ASYNC   | SYNC     | N/A       | false |
         * | ASYNC   | SYNC     | SYNC      | false |
         * | ASYNC   | SYNC     | ASYNC     | true  |
         * | ASYNC   | ASYNC    | N/A       | true  |
         * | ASYNC   | ASYNC    | SYNC      | false |
         * | ASYNC   | ASYNC    | ASYNC     | true  |
         * | SYNC    | N/A      | N/A       | false |
         * | SYNC    | N/A      | SYNC      | false |
         * | SYNC    | N/A      | ASYNC     | true  |
         * | SYNC    | SYNC     | N/A       | false |
         * | SYNC    | SYNC     | SYNC      | false |
         * | SYNC    | SYNC     | ASYNC     | true  |
         * | SYNC    | ASYNC    | N/A       | true  |
         * | SYNC    | ASYNC    | SYNC      | false |
         * | SYNC    | ASYNC    | ASYNC     | true  |
         */
        fun of(
            label: MutationMode,
            global: MutationMode?,
            request: MutationMode?,
        ): MutationModeContext {
            val effectiveMode = request ?: global
            val queue =
                when (label) {
                    ASYNC ->
                        when (effectiveMode) {
                            null -> true
                            SYNC -> false
                            ASYNC -> true
                            IGNORE -> true
                        }

                    SYNC ->
                        when (effectiveMode) {
                            null -> false
                            SYNC -> false
                            ASYNC -> true
                            IGNORE -> true
                        }

                    IGNORE ->
                        when (effectiveMode) {
                            null -> true
                            SYNC -> throw IllegalArgumentException("SYNC is not allowed in IGNORE mode.")
                            ASYNC -> true
                            IGNORE -> true
                        }
                }
            return MutationModeContext(label, global, request, queue)
        }
    }
}
