package com.kakao.actionbase.engine.metadata

import com.kakao.actionbase.engine.metadata.MutationMode.ASYNC
import com.kakao.actionbase.engine.metadata.MutationMode.IGNORE
import com.kakao.actionbase.engine.metadata.MutationMode.SYNC

data class MutationModeContext private constructor(
    val label: MutationMode,
    val request: MutationMode?,
    val global: MutationMode?,
    val internal: MutationMode?,
    val queue: Boolean,
) {
    companion object {
        /**
         * Priority: internal > global > request > label (table)
         *
         * mode  = internal ?: global ?: request ?: label
         * queue = mode == ASYNC || mode == IGNORE
         */
        fun of(
            label: MutationMode,
            request: MutationMode?,
        ): MutationModeContext = of(label, request, global = null, internal = null)

        fun of(
            label: MutationMode,
            request: MutationMode?,
            global: MutationMode?,
            internal: MutationMode?,
        ): MutationModeContext {
            require(request == null || internal == null) {
                "request and internal are mutually exclusive. request=$request, internal=$internal"
            }
            val mode = internal ?: global ?: request ?: label
            require(!(internal == null && global == null && request == SYNC && label == IGNORE)) {
                "SYNC is not allowed when table mode is IGNORE."
            }
            val queue = mode == ASYNC || mode == IGNORE
            return MutationModeContext(label, request, global, internal, queue)
        }
    }
}
