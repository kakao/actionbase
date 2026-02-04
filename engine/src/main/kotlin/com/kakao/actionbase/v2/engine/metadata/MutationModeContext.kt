package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.MutationMode.ASYNC
import com.kakao.actionbase.v2.core.metadata.MutationMode.IGNORE
import com.kakao.actionbase.v2.core.metadata.MutationMode.SYNC

data class MutationModeContext(
    val l: MutationMode, // label (table)
    val g: MutationMode?, // global
    val r: MutationMode?, // request
    val i: MutationMode?, // internal
    val queue: Boolean,
) {
    companion object {
        /**
         * Priority: i(nternal) > g(lobal) > r(equest) > t(able)
         *
         * mode  = internal ?: global ?: request ?: table
         * queue = mode == ASYNC || mode == IGNORE
         *
         * Constraint: request and internal are mutually exclusive (both non-null -> IllegalArgumentException)
         * Constraint: table=IGNORE && internal=N/A && global=N/A && request=SYNC -> IllegalArgumentException
         *
         * | internal | global | request | table  | mode   | queue   |
         * | -------- | ------ | ------- | ------ | ------ | ------- |
         * | SYNC     | *      | *       | *      | SYNC   | false   |
         * | ASYNC    | *      | *       | *      | ASYNC  | true    |
         * | IGNORE   | *      | *       | *      | IGNORE | true    |
         * | N/A      | SYNC   | *       | *      | SYNC   | false   |
         * | N/A      | ASYNC  | *       | *      | ASYNC  | true    |
         * | N/A      | IGNORE | *       | *      | IGNORE | true    |
         * | N/A      | N/A    | SYNC    | SYNC   | SYNC   | false   |
         * | N/A      | N/A    | SYNC    | ASYNC  | SYNC   | false   |
         * | N/A      | N/A    | SYNC    | IGNORE | SYNC   | Invalid |
         * | N/A      | N/A    | ASYNC   | *      | ASYNC  | true    |
         * | N/A      | N/A    | IGNORE  | *      | IGNORE | true    |
         * | N/A      | N/A    | N/A     | SYNC   | SYNC   | false   |
         * | N/A      | N/A    | N/A     | ASYNC  | ASYNC  | true    |
         * | N/A      | N/A    | N/A     | IGNORE | IGNORE | true    |
         */
        fun of(
            label: MutationMode,
            global: MutationMode?,
            request: MutationMode?,
            internal: MutationMode? = null,
        ): MutationModeContext {
            require(request == null || internal == null) {
                "request and internal are mutually exclusive. request=$request, internal=$internal"
            }
            val mode = internal ?: global ?: request ?: label
            require(!(label == IGNORE && internal == null && global == null && request == SYNC)) {
                "SYNC is not allowed when table mode is IGNORE."
            }
            val queue = mode == ASYNC || mode == IGNORE
            return MutationModeContext(label, global, request, internal, queue)
        }
    }
}
