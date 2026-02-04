package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.MutationMode.ASYNC
import com.kakao.actionbase.v2.core.metadata.MutationMode.IGNORE
import com.kakao.actionbase.v2.core.metadata.MutationMode.SYNC

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * MutationModeContext.of() tests
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
class MutationModeContextSpec :
    StringSpec({

        val modes = MutationMode.entries
        val nullableModes = modes + null

        fun testLabel(
            i: MutationMode?,
            g: MutationMode?,
            r: MutationMode?,
            t: MutationMode,
        ) = "i=${i ?: "N/A"}, g=${g ?: "N/A"}, r=${r ?: "N/A"}, t=$t"

        // TC 1: i=SYNC, g=*, r=N/A, t=* -> mode=SYNC, queue=false
        // (request must be null when internal is specified)
        for (g in nullableModes) {
            for (t in modes) {
                "${testLabel(SYNC, g, null, t)} -> mode=SYNC, queue=false" {
                    val ctx = MutationModeContext.of(label = t, global = g, request = null, internal = SYNC)
                    ctx.queue shouldBe false
                }
            }
        }

        // TC 2: i=ASYNC, g=*, r=N/A, t=* -> mode=ASYNC, queue=true
        for (g in nullableModes) {
            for (t in modes) {
                "${testLabel(ASYNC, g, null, t)} -> mode=ASYNC, queue=true" {
                    val ctx = MutationModeContext.of(label = t, global = g, request = null, internal = ASYNC)
                    ctx.queue shouldBe true
                }
            }
        }

        // TC 3: i=IGNORE, g=*, r=N/A, t=* -> mode=IGNORE, queue=true
        for (g in nullableModes) {
            for (t in modes) {
                "${testLabel(IGNORE, g, null, t)} -> mode=IGNORE, queue=true" {
                    val ctx = MutationModeContext.of(label = t, global = g, request = null, internal = IGNORE)
                    ctx.queue shouldBe true
                }
            }
        }

        // TC 4: i=N/A, g=SYNC, r=*, t=* -> mode=SYNC, queue=false
        for (r in nullableModes) {
            for (t in modes) {
                "${testLabel(null, SYNC, r, t)} -> mode=SYNC, queue=false" {
                    val ctx = MutationModeContext.of(label = t, global = SYNC, request = r, internal = null)
                    ctx.queue shouldBe false
                }
            }
        }

        // TC 5: i=N/A, g=ASYNC, r=*, t=* -> mode=ASYNC, queue=true
        for (r in nullableModes) {
            for (t in modes) {
                "${testLabel(null, ASYNC, r, t)} -> mode=ASYNC, queue=true" {
                    val ctx = MutationModeContext.of(label = t, global = ASYNC, request = r, internal = null)
                    ctx.queue shouldBe true
                }
            }
        }

        // TC 6: i=N/A, g=IGNORE, r=*, t=* -> mode=IGNORE, queue=true
        for (r in nullableModes) {
            for (t in modes) {
                "${testLabel(null, IGNORE, r, t)} -> mode=IGNORE, queue=true" {
                    val ctx = MutationModeContext.of(label = t, global = IGNORE, request = r, internal = null)
                    ctx.queue shouldBe true
                }
            }
        }

        // TC: request and internal are mutually exclusive
        for (r in modes) {
            for (i in modes) {
                "request=$r and internal=$i both non-null -> throws IllegalArgumentException" {
                    shouldThrow<IllegalArgumentException> {
                        MutationModeContext.of(label = SYNC, global = null, request = r, internal = i)
                    }
                }
            }
        }

        // TC 7: i=N/A, g=N/A, r=SYNC, t={SYNC,ASYNC} -> mode=SYNC, queue=false
        "${testLabel(null, null, SYNC, SYNC)} -> mode=SYNC, queue=false" {
            MutationModeContext.of(label = SYNC, global = null, request = SYNC, internal = null).queue shouldBe false
        }

        "${testLabel(null, null, SYNC, ASYNC)} -> mode=SYNC, queue=false" {
            MutationModeContext.of(label = ASYNC, global = null, request = SYNC, internal = null).queue shouldBe false
        }

        // TC 7-Invalid: i=N/A, g=N/A, r=SYNC, t=IGNORE -> throws
        "${testLabel(null, null, SYNC, IGNORE)} -> throws IllegalArgumentException" {
            shouldThrow<IllegalArgumentException> {
                MutationModeContext.of(label = IGNORE, global = null, request = SYNC, internal = null)
            }
        }

        // TC 8: i=N/A, g=N/A, r=ASYNC, t=* -> mode=ASYNC, queue=true
        for (t in modes) {
            "${testLabel(null, null, ASYNC, t)} -> mode=ASYNC, queue=true" {
                MutationModeContext.of(label = t, global = null, request = ASYNC, internal = null).queue shouldBe true
            }
        }

        // TC 9: i=N/A, g=N/A, r=IGNORE, t=* -> mode=IGNORE, queue=true
        for (t in modes) {
            "${testLabel(null, null, IGNORE, t)} -> mode=IGNORE, queue=true" {
                MutationModeContext.of(label = t, global = null, request = IGNORE, internal = null).queue shouldBe true
            }
        }

        // TC 10: i=N/A, g=N/A, r=N/A, t=* -> mode=table, queue depends on table
        "${testLabel(null, null, null, SYNC)} -> mode=SYNC, queue=false" {
            MutationModeContext.of(label = SYNC, global = null, request = null, internal = null).queue shouldBe false
        }

        "${testLabel(null, null, null, ASYNC)} -> mode=ASYNC, queue=true" {
            MutationModeContext.of(label = ASYNC, global = null, request = null, internal = null).queue shouldBe true
        }

        "${testLabel(null, null, null, IGNORE)} -> mode=IGNORE, queue=true" {
            MutationModeContext.of(label = IGNORE, global = null, request = null, internal = null).queue shouldBe true
        }
    })
