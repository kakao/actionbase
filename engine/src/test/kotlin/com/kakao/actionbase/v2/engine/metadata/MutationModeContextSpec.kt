package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.metadata.MutationMode.ASYNC
import com.kakao.actionbase.v2.core.metadata.MutationMode.SYNC

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * MutationModeContext.of() tests
 *
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
class MutationModeContextSpec :
    StringSpec({

        // t=ASYNC, g=N/A
        "t=ASYNC, g=N/A, r=N/A -> queue=true" {
            val context = MutationModeContext.of(label = ASYNC, global = null, request = null)
            context.queue shouldBe true
        }

        "t=ASYNC, g=N/A, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = ASYNC, global = null, request = SYNC)
            context.queue shouldBe false
        }

        "t=ASYNC, g=N/A, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = ASYNC, global = null, request = ASYNC)
            context.queue shouldBe true
        }

        // t=ASYNC, g=SYNC
        "t=ASYNC, g=SYNC, r=N/A -> queue=false" {
            val context = MutationModeContext.of(label = ASYNC, global = SYNC, request = null)
            context.queue shouldBe false
        }

        "t=ASYNC, g=SYNC, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = ASYNC, global = SYNC, request = SYNC)
            context.queue shouldBe false
        }

        "t=ASYNC, g=SYNC, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = ASYNC, global = SYNC, request = ASYNC)
            context.queue shouldBe true
        }

        // t=ASYNC, g=ASYNC
        "t=ASYNC, g=ASYNC, r=N/A -> queue=true" {
            val context = MutationModeContext.of(label = ASYNC, global = ASYNC, request = null)
            context.queue shouldBe true
        }

        "t=ASYNC, g=ASYNC, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = ASYNC, global = ASYNC, request = SYNC)
            context.queue shouldBe false
        }

        "t=ASYNC, g=ASYNC, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = ASYNC, global = ASYNC, request = ASYNC)
            context.queue shouldBe true
        }

        // t=SYNC, g=N/A
        "t=SYNC, g=N/A, r=N/A -> queue=false" {
            val context = MutationModeContext.of(label = SYNC, global = null, request = null)
            context.queue shouldBe false
        }

        "t=SYNC, g=N/A, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = SYNC, global = null, request = SYNC)
            context.queue shouldBe false
        }

        "t=SYNC, g=N/A, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = SYNC, global = null, request = ASYNC)
            context.queue shouldBe true
        }

        // t=SYNC, g=SYNC
        "t=SYNC, g=SYNC, r=N/A -> queue=false" {
            val context = MutationModeContext.of(label = SYNC, global = SYNC, request = null)
            context.queue shouldBe false
        }

        "t=SYNC, g=SYNC, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = SYNC, global = SYNC, request = SYNC)
            context.queue shouldBe false
        }

        "t=SYNC, g=SYNC, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = SYNC, global = SYNC, request = ASYNC)
            context.queue shouldBe true
        }

        // t=SYNC, g=ASYNC
        "t=SYNC, g=ASYNC, r=N/A -> queue=true" {
            val context = MutationModeContext.of(label = SYNC, global = ASYNC, request = null)
            context.queue shouldBe true
        }

        "t=SYNC, g=ASYNC, r=SYNC -> queue=false" {
            val context = MutationModeContext.of(label = SYNC, global = ASYNC, request = SYNC)
            context.queue shouldBe false
        }

        "t=SYNC, g=ASYNC, r=ASYNC -> queue=true" {
            val context = MutationModeContext.of(label = SYNC, global = ASYNC, request = ASYNC)
            context.queue shouldBe true
        }
    })
