package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.MutableClock

import java.time.Clock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

/**
 * What alice and bob do to a per-entity ranking, and what they see afterwards. One declaration throughout:
 * a day bucket in UTC, a 365-day `now` window, ranked by item and split by category.
 *
 * A metric is never incremented. Every call recomputes it from the purchases still inside the window, so a
 * ranking changes only when a purchase or a refresh recomputes it, and the window itself moves only when
 * the bucket does. Most of these are pinning one of those two sentences.
 *
 * Each test buys under an item and a category of its own, so they can run in any order and alone.
 *
 * Declarations other than this one are in [TopkDeclarationTest], calls that are refused rather than
 * answered in [TopkApiContractE2ETest], and failures that need a stubbed storage in
 * `TopkAggregationHandlerTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TopkFixture.MovableClock::class)
class TopkScenarioTest : E2ETestBase() {
    @Autowired
    private lateinit var injectedClock: Clock

    private lateinit var topk: TopkFixture

    @BeforeAll
    fun setup() {
        topk = TopkFixture(client, injectedClock as MutableClock)
        topk.createRefreshQueue()
        topk.declare(DAY)
    }

    /** Buying, cancelling and reading back, all on one day, so nothing here depends on the window. */
    @Nested
    inner class WritingAndReading {
        @Test
        fun `an apple alice buys twice counts twice`() {
            topk.buy("alice", "apple", at = PURCHASED_AT, dimensionValues = "buys_twice")
            assertEquals(1L, topk.metric("alice", "apple", dimensionValues = "buys_twice"))

            topk.buy("alice", "apple", at = PURCHASED_AT, dimensionValues = "buys_twice")

            assertEquals(2L, topk.metric("alice", "apple", dimensionValues = "buys_twice"))
        }

        @Test
        fun `a pear joins the ranking her apple is in, and a shirt starts one of its own`() {
            topk.buy("alice", "cherry", at = PURCHASED_AT, dimensionValues = "two_items")
            topk.buy("alice", "pear", at = PURCHASED_AT, dimensionValues = "two_items")
            topk.buy("alice", "shirt", dimensionValues = "cloth", at = PURCHASED_AT)

            assertEquals(1L, topk.metric("alice", "cherry", dimensionValues = "two_items"))
            assertEquals(1L, topk.metric("alice", "pear", dimensionValues = "two_items"))
            assertEquals(1L, topk.metric("alice", "shirt", dimensionValues = "cloth"))
        }

        @Test
        fun `bob buying the same plum leaves her ranking alone`() {
            topk.buy("alice", "plum", at = PURCHASED_AT, dimensionValues = "two_buyers")
            topk.buy("bob", "plum", at = PURCHASED_AT, dimensionValues = "two_buyers")

            assertEquals(1L, topk.metric("alice", "plum", dimensionValues = "two_buyers"))
            assertEquals(1L, topk.metric("bob", "plum", dimensionValues = "two_buyers"))
        }

        @Test
        fun `cancelling takes a fig back out, and cancelling the last one leaves a zero`() {
            val first = topk.buy("alice", "fig", at = PURCHASED_AT, dimensionValues = "cancels")
            val second = topk.buy("alice", "fig", at = PURCHASED_AT, dimensionValues = "cancels")

            topk.cancel("alice", "fig", first, dimensionValues = "cancels")
            assertEquals(1L, topk.metric("alice", "fig", dimensionValues = "cancels"))

            topk.cancel("alice", "fig", second, dimensionValues = "cancels")

            assertEquals(0L, topk.metric("alice", "fig", dimensionValues = "cancels"))
        }

        @Test
        fun `a melon bought again ten days later counts twice`() {
            topk.buy("alice", "melon", at = PURCHASED_AT, dimensionValues = "two_days")
            topk.buy("alice", "melon", at = "2026-01-11T14:32:00Z", dimensionValues = "two_days")

            assertEquals(2L, topk.metric("alice", "melon", dimensionValues = "two_days"))
        }

        @Test
        fun `her ranking reads back by metric, and a limit cuts it to the head`() {
            topk.buy("alice", "papaya", dimensionValues = "reads", at = PURCHASED_AT)
            topk.buy("alice", "papaya", dimensionValues = "reads", at = PURCHASED_AT)
            topk.buy("alice", "guava", dimensionValues = "reads", at = PURCHASED_AT)

            val ranking = topk.read("alice", "reads")

            assertEquals(listOf("papaya", "guava"), ranking.topks.map { it.value })
            assertEquals(listOf(2L, 1L), ranking.topks.map { it.metric })

            val head = topk.read("alice", "reads", limit = 1)

            assertEquals(1, head.count)
            assertEquals("papaya", head.topks.single().value)
        }

        @Test
        fun `a category she never bought in has no ranking at all`() {
            topk.buy("alice", "lychee", at = PURCHASED_AT, dimensionValues = "never_bought")

            assertNull(topk.metric("alice", "lychee", dimensionValues = "cloth"))
        }

        /** Nothing declares this: the index sorts by metric, and rows sharing one land in key order. */
        @Test
        fun `two items on the same metric come back in order of the item`() {
            topk.buy("alice", "peach", dimensionValues = "ties", at = PURCHASED_AT)
            topk.buy("alice", "apricot", dimensionValues = "ties", at = PURCHASED_AT)

            assertEquals(listOf("apricot", "peach"), topk.read("alice", "ties").topks.map { it.value })
        }

        @Test
        fun `the category the declaration carries rides along on the ranking`() {
            topk.buy("alice", "banana", at = PURCHASED_AT, dimensionValues = "carries")

            assertEquals(
                "carries",
                topk
                    .read("alice", "carries")
                    .topks
                    .single { it.value == "banana" }
                    .properties["category"],
            )
        }
    }

    /**
     * The window is read at the bucket's precision, so a purchase stays inside for the whole bucket it
     * turns as old as the window in and leaves in the next one. Nothing notices until a refresh runs.
     */
    @Nested
    inner class TimePassing {
        @Test
        fun `a kiwi is still ranked a day short of a year`() {
            topk.buy("alice", "kiwi", at = PURCHASED_AT, dimensionValues = "short_of_a_year")

            topk.now("2026-12-31T14:32:00Z")
            topk.refresh("alice", "kiwi", dimensionValues = "short_of_a_year")

            assertEquals(1L, topk.metric("alice", "kiwi", dimensionValues = "short_of_a_year"))
        }

        @Test
        fun `a date is still ranked on the day it turns a year old`() {
            topk.buy("alice", "date", at = PURCHASED_AT, dimensionValues = "a_year_old")

            topk.now("2027-01-01T14:32:00Z")
            topk.refresh("alice", "date", dimensionValues = "a_year_old")

            assertEquals(1L, topk.metric("alice", "date", dimensionValues = "a_year_old"))
        }

        @Test
        fun `a mango drops off the day after that, and buying it again brings it back`() {
            topk.buy("alice", "mango", at = PURCHASED_AT, dimensionValues = "past_a_year")

            topk.now("2027-01-02T00:00:00Z")
            topk.refresh("alice", "mango", dimensionValues = "past_a_year")
            assertEquals(0L, topk.metric("alice", "mango", dimensionValues = "past_a_year"))

            topk.buy("alice", "mango", at = "2027-01-02T14:32:00Z", dimensionValues = "past_a_year")

            assertEquals(1L, topk.metric("alice", "mango", dimensionValues = "past_a_year"))
        }

        @Test
        fun `a year of buying grapes loses them one at a time as each ages out`() {
            topk.buy("alice", "grape", at = PURCHASED_AT, dimensionValues = "a_year_of_buying")
            topk.buy("alice", "grape", at = "2026-06-01T14:32:00Z", dimensionValues = "a_year_of_buying")
            topk.buy("alice", "grape", at = "2026-12-01T14:32:00Z", dimensionValues = "a_year_of_buying")
            assertEquals(3L, topk.metric("alice", "grape", dimensionValues = "a_year_of_buying"))

            topk.now("2027-01-02T00:00:00Z")
            topk.refresh("alice", "grape", dimensionValues = "a_year_of_buying")
            assertEquals(2L, topk.metric("alice", "grape", dimensionValues = "a_year_of_buying"))

            topk.now("2027-06-02T00:00:00Z")
            topk.refresh("alice", "grape", dimensionValues = "a_year_of_buying")
            assertEquals(1L, topk.metric("alice", "grape", dimensionValues = "a_year_of_buying"))
        }

        @Test
        fun `a refresh the day after she bought a lime changes nothing`() {
            topk.buy("alice", "lime", at = PURCHASED_AT, dimensionValues = "early_refresh")

            topk.now("2026-01-02T14:32:00Z")
            topk.refresh("alice", "lime", dimensionValues = "early_refresh")

            assertEquals(1L, topk.metric("alice", "lime", dimensionValues = "early_refresh"))
        }

        @Test
        fun `a refresh long past the day it was due still drops the orange`() {
            topk.buy("alice", "orange", at = PURCHASED_AT, dimensionValues = "late_refresh")

            topk.now("2027-06-01T00:00:00Z")
            topk.refresh("alice", "orange", dimensionValues = "late_refresh")

            assertEquals(0L, topk.metric("alice", "orange", dimensionValues = "late_refresh"))
        }

        @Test
        fun `a year on, her quince still reads as it did until a refresh runs`() {
            topk.buy("alice", "quince", at = PURCHASED_AT, dimensionValues = "no_refresh")

            topk.now("2027-01-02T14:32:00Z")

            assertEquals(1L, topk.metric("alice", "quince", dimensionValues = "no_refresh"))
        }

        @Test
        fun `refreshing her raisin twice lands on the same metric`() {
            topk.buy("alice", "raisin", at = PURCHASED_AT, dimensionValues = "twice_refreshed")
            topk.buy("alice", "raisin", at = PURCHASED_AT, dimensionValues = "twice_refreshed")

            topk.refresh("alice", "raisin", dimensionValues = "twice_refreshed")
            assertEquals(2L, topk.metric("alice", "raisin", dimensionValues = "twice_refreshed"))

            topk.refresh("alice", "raisin", dimensionValues = "twice_refreshed")

            assertEquals(2L, topk.metric("alice", "raisin", dimensionValues = "twice_refreshed"))
        }
    }

    /**
     * A purchase queues its own refresh for the instant it leaves the window: its bucket's start, plus the
     * window, plus one more bucket. A ranking that cannot go stale queues nothing.
     */
    @Nested
    inner class RefreshScheduling {
        /** a refresh is only worth queueing for a ranking that can go stale. */
        @Test
        fun `nothing is queued for a ranking that cannot go stale`() {
            listOf(
                DAY.copy(database = "topk_fixed", window = "purchasedAt:bt:2026-01-01,2026-12-31"),
                DAY.copy(database = "topk_norefresh", refreshAfterMillis = -1),
                DAY.copy(database = "topk_nobucket", bucketed = false, window = null),
            ).forEach { shape ->
                val declaration = topk.declare(shape)
                val buyer = "queues_nothing_${declaration.database}"

                topk.buy(buyer, "apple", at = PURCHASED_AT, declaration = declaration)

                assertEquals(1L, topk.metric(buyer, "apple", declaration = declaration), declaration.database)
                assertEquals(emptyList<String>(), topk.refreshAt(buyer, declaration), declaration.database)
            }
        }

        @Test
        fun `one purchase is due a bucket after the window ends`() {
            topk.buy("queues_once", "apple", at = PURCHASED_AT)

            assertEquals(listOf(REFRESH_AT), topk.refreshAt("queues_once"))
        }

        @Test
        fun `two purchases in one bucket are due together`() {
            topk.buy("queues_twice", "apple", at = PURCHASED_AT)
            topk.buy("queues_twice", "apple", at = PURCHASED_AT)

            assertEquals(listOf(REFRESH_AT, REFRESH_AT), topk.refreshAt("queues_twice"))
        }

        @Test
        fun `purchases in two buckets are due a bucket apart`() {
            topk.buy("queues_two_days", "apple", at = PURCHASED_AT)
            topk.buy("queues_two_days", "apple", at = "2026-01-11T14:32:00Z")

            assertEquals(listOf(REFRESH_AT, "2027-01-12T00:00:00Z"), topk.refreshAt("queues_two_days"))
        }

        @Test
        fun `two items bought in one bucket are due together but counted apart`() {
            topk.buy("queues_two_items", "apple", at = PURCHASED_AT)
            topk.buy("queues_two_items", "pear", at = PURCHASED_AT)

            assertEquals(listOf(REFRESH_AT, REFRESH_AT), topk.refreshAt("queues_two_items"))
        }

        @Test
        fun `a refresh queues nothing of its own`() {
            topk.buy("queues_after_refresh", "apple", at = PURCHASED_AT)

            topk.refresh("queues_after_refresh", "apple")

            assertEquals(listOf(REFRESH_AT), topk.refreshAt("queues_after_refresh"))
        }

        /**
         * A sweeper reads the queue bounded by its own clock, so the due time decides which run picks a
         * ranking up. A millisecond short of it there is nothing to do, and scheduling from when the
         * aggregation ran rather than from the bucket the purchase fell in would hand it work this early.
         */
        @Test
        fun `a sweeper on the clock picks a ranking up the instant it falls due and not before`() {
            topk.buy("swept_when_due", "apple", at = PURCHASED_AT)

            topk.now("2027-01-01T23:59:59.999Z")
            assertEquals(emptyList<String>(), topk.dueRefreshes("swept_when_due"))

            topk.now(REFRESH_AT)
            assertEquals(listOf(REFRESH_AT), topk.dueRefreshes("swept_when_due"))
        }

        @Test
        fun `the category the declaration carries comes back on the message`() {
            topk.buy("queues_property", "apple", at = PURCHASED_AT)

            assertEquals(
                mapOf("category" to "fruit"),
                topk.refreshMessages(DAY, "queues_property").single().let {
                    ((it.value as Map<*, *>)["item"] as Map<*, *>)["properties"]
                },
            )
        }
    }

    /**
     * Values a producer sends that the key was not written for. An entity id and a category are whatever
     * the data says, and a purchase can be corrected after the fact.
     */
    @Nested
    inner class UnexpectedData {
        /** Without escaping, `alice|1` with `fruit` and `alice` with `1|fruit` would build one key. */
        @Test
        fun `a category holding the separator keeps a ranking of its own`() {
            topk.buy("alice|1", "tangerine", at = PURCHASED_AT)
            topk.buy("alice", "tangerine", dimensionValues = "1|fruit", at = PURCHASED_AT)

            assertEquals(1L, topk.metric("alice|1", "tangerine"))
            assertEquals(1L, topk.metric("alice", "tangerine", dimensionValues = "1|fruit"))
        }

        @Test
        fun `a purchase with no category still lands in a ranking`() {
            topk.buy("alice", "nectarine", dimensionValues = null, at = PURCHASED_AT)

            assertEquals(1L, topk.metric("alice", "nectarine", dimensionValues = null))
        }

        /**
         * A recompute only knows the category it was handed, so the ranking the purchase left keeps its
         * row until that one is recomputed too. The value from before the edit — a CDC pre-image — is
         * what a consumer sends to do it.
         */
        @Test
        fun `moving a purchase to another category needs the old category aggregated too`() {
            val id = topk.buy("alice", "coconut", at = PURCHASED_AT)

            topk.recategorize(id, "alice", "coconut", to = "cloth")
            topk.aggregate("alice", "coconut", "cloth", DAY)

            assertEquals(1L, topk.metric("alice", "coconut", dimensionValues = "cloth"))
            assertEquals(1L, topk.metric("alice", "coconut"))

            topk.aggregate("alice", "coconut", "fruit", DAY)

            assertEquals(0L, topk.metric("alice", "coconut"))
        }

        /** The bucket value is not part of the key, so correcting it needs no pre-image. */
        @Test
        fun `correcting when a purchase happened keeps the ranking and leaves the old due time queued`() {
            val id = topk.buy("corrects_time", "apple", at = PURCHASED_AT)

            topk.correctPurchaseTime(id, "corrects_time", "apple", at = PURCHASED_AT_LATER)
            topk.aggregate("corrects_time", "apple", "fruit", DAY)

            assertEquals(1L, topk.metric("corrects_time", "apple"))
            assertEquals(listOf(REFRESH_AT, "2027-01-12T00:00:00Z"), topk.refreshAt("corrects_time"))
        }
    }
}
