package com.kakao.actionbase.engine.metadata

import com.kakao.actionbase.engine.metadata.MutationMode.ASYNC
import com.kakao.actionbase.engine.metadata.MutationMode.IGNORE
import com.kakao.actionbase.engine.metadata.MutationMode.SYNC

data class MutationModeContext private constructor(
    val label: MutationMode,
    val request: MutationMode?,
    val system: MutationMode?,
    val force: Boolean,
    val queue: Boolean,
) {
    companion object {
        /**
         * Priority: request(force=true) > system > request(force=false) > label (table)
         *
         * Constraints:
         *   - force==true && request==null -> IllegalArgumentException
         *   - force==false && system==null && request==SYNC && label==IGNORE -> IllegalArgumentException
         *
         * queue = mode == ASYNC || mode == IGNORE
         *
         * | force(request) | system | request | label  | mode   | queue   |
         * | -------------- | ------ | ------- | ------ | ------ | ------- |
         * | true           | *      | SYNC    | *      | SYNC   | false   |
         * | true           | *      | ASYNC   | *      | ASYNC  | true    |
         * | true           | *      | N/A     | *      | —      | Invalid |
         * | false          | SYNC   | *       | *      | SYNC   | false   |
         * | false          | ASYNC  | *       | *      | ASYNC  | true    |
         * | false          | IGNORE | *       | *      | IGNORE | true    |
         * | false          | N/A    | SYNC    | SYNC   | SYNC   | false   |
         * | false          | N/A    | SYNC    | ASYNC  | SYNC   | false   |
         * | false          | N/A    | SYNC    | IGNORE | —      | Invalid |
         * | false          | N/A    | ASYNC   | *      | ASYNC  | true    |
         * | false          | N/A    | IGNORE  | *      | IGNORE | true    |
         * | false          | N/A    | N/A     | SYNC   | SYNC   | false   |
         * | false          | N/A    | N/A     | ASYNC  | ASYNC  | true    |
         * | false          | N/A    | N/A     | IGNORE | IGNORE | true    |
         */
        fun of(
            label: MutationMode,
            request: MutationMode?,
            system: MutationMode? = null,
            force: Boolean = false,
        ): MutationModeContext {
            require(!(force && request == null)) {
                "force requires a non-null request. force=$force, request=$request"
            }
            val isSyncRequestOnIgnoreTable = !force && system == null && request == SYNC && label == IGNORE
            require(!isSyncRequestOnIgnoreTable) {
                "SYNC request is not allowed when table mode is IGNORE without force or system override."
            }
            val mode =
                when {
                    force -> request!!
                    system != null -> system
                    request != null -> request
                    else -> label
                }
            val queue = mode == ASYNC || mode == IGNORE
            return MutationModeContext(label, request, system, force, queue)
        }
    }
}
