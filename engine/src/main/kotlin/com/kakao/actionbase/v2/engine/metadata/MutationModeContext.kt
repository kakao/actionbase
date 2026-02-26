package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.MutationMode.ASYNC
import com.kakao.actionbase.v2.core.metadata.MutationMode.IGNORE
import com.kakao.actionbase.v2.core.metadata.MutationMode.SYNC

// TODO: Add @ConsistentCopyVisibility annotation when Kotlin is upgraded to 1.9.20+.
//       In Kotlin 2.5, the generated copy() method will expose the private constructor.
data class MutationModeContext private constructor(
    val l: MutationMode, // label (table)
    val r: MutationMode?, // request
    val s: MutationMode?, // system
    val f: Boolean, // force
    val queue: Boolean,
) {
    companion object {
        /**
         * Priority: request(force=true) > system > request(force=false) > label (table)
         *
         * Constraints:
         *   - force==true && request==null -> IllegalArgumentException
         *   - force==false && system==null && request==SYNC && table==IGNORE -> IllegalArgumentException
         *
         * queue = mode == ASYNC || mode == IGNORE
         *
         * | force(request) | system | request | table  | mode   | queue   |
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
            table: MutationMode,
            request: MutationMode?,
            system: MutationMode? = null,
            force: Boolean = false,
        ): MutationModeContext {
            require(!(force && request == null)) {
                "force requires a non-null request. force=$force, request=$request"
            }
            val isSyncRequestOnIgnoreTable = !force && system == null && request == SYNC && table == IGNORE
            require(!isSyncRequestOnIgnoreTable) {
                "SYNC request is not allowed when table mode is IGNORE without force or system override."
            }
            val mode =
                when {
                    force -> request!!
                    system != null -> system
                    request != null -> request
                    else -> table
                }
            val queue = mode == ASYNC || mode == IGNORE
            return MutationModeContext(l = table, r = request, s = system, f = force, queue = queue)
        }
    }
}
