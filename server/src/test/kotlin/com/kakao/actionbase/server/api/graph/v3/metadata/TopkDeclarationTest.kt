package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.MutableClock
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import java.time.Clock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType

/**
 * What the declaration itself decides, rather than what a buyer does: who a ranking belongs to, what it
 * ranks, how coarse its bucket is, and what happens to rankings that already exist when it changes. What a
 * buyer does is in [TopkScenarioTest].
 *
 * Two of those are worth reading together. A direction decides who a ranking belongs to, and `entity` only
 * decides whether all of them land on one row — which is why a global ranking is declared `IN`, counting
 * every buyer of an item rather than one buyer's own.
 *
 * Each declaration gets a database of its own, so a test that changes one cannot reach another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TopkFixture.MovableClock::class)
class TopkDeclarationTest : E2ETestBase() {
    @Autowired
    private lateinit var injectedClock: Clock

    private lateinit var topk: TopkFixture

    @BeforeAll
    fun setup() {
        topk = TopkFixture(client, injectedClock as MutableClock)
        topk.createRefreshQueue()
    }

    /** A global ranking is one row for everyone, and naming an entity when reading it does not narrow it. */
    @Test
    fun `a global ranking counts every buyer, whoever is named when it is read`() {
        val global = topk.declare(DAY.copy(database = "topk_global", direction = "IN", entity = "__GLOBAL__"))

        topk.buy("alice", "apple", at = PURCHASED_AT, declaration = global)
        topk.buy("bob", "apple", at = PURCHASED_AT, declaration = global)

        assertEquals(2L, topk.metric("alice", "apple", declaration = global))
        assertEquals(2L, topk.metric("nobody_at_all", "apple", declaration = global))
    }

    @Test
    fun `declaring a top-K over data already there fills nothing in until something recomputes`() {
        val backfill = topk.declare(DAY.copy(database = "topk_backfill", declared = false))

        topk.buy("backfiller", "apple", at = PURCHASED_AT, declaration = backfill)
        topk.buy("backfiller", "apple", at = PURCHASED_AT, declaration = backfill)

        topk.alter(backfill.copy(declared = true))

        assertNull(topk.metric("backfiller", "apple", declaration = backfill))

        topk.aggregate("backfiller", "apple", "fruit", backfill)

        assertEquals(2L, topk.metric("backfiller", "apple", declaration = backfill))
    }

    @Test
    fun `shortening the window drops what no longer fits on the next recompute`() {
        val declaration = topk.declare(DAY.copy(database = "topk_alterwindow"))

        topk.buy("window_changer", "apple", at = PURCHASED_AT, declaration = declaration)
        topk.buy("window_changer", "apple", at = PURCHASED_AT_LATER, declaration = declaration)
        assertEquals(2L, topk.metric("window_changer", "apple", declaration = declaration))

        topk.alter(declaration.copy(window = "purchasedAt:bt:now-5d,now"))
        topk.aggregate("window_changer", "apple", "fruit", declaration)

        assertEquals(1L, topk.metric("window_changer", "apple", declaration = declaration))
    }

    /** Messages already queued keep the due time they were written with. */
    @Test
    fun `changing refreshAfterMillis only moves the due time of what is enqueued after it`() {
        val declaration = topk.declare(DAY.copy(database = "topk_alterrefresh"))

        topk.buy("refresh_changer", "apple", at = PURCHASED_AT, declaration = declaration)

        topk.alter(declaration.copy(refreshAfterMillis = DAY_MILLIS))
        topk.buy("refresh_changer", "apple", at = PURCHASED_AT, declaration = declaration)

        assertEquals(
            listOf("2026-01-03T00:00:00Z", REFRESH_AT),
            topk.refreshAt("refresh_changer", declaration),
        )
    }

    /** The rows stay where they were, but nothing can name the top-K they belong to. */
    @Test
    fun `dropping the declaration refuses the read and makes a sweep skip`() {
        val declaration = topk.declare(DAY.copy(database = "topk_dropdecl"))

        topk.buy("dropper", "apple", at = PURCHASED_AT, declaration = declaration)

        topk.alter(declaration.copy(declared = false))

        client
            .post()
            .uri("/graph/v3/query/${topk.preparedRead(declaration, TOPK, declaration.split)}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"arguments": {"entity": "dropper", "limit": 10, "$DIMENSION_VALUE": "fruit"}}""")
            .exchange()
            .expectStatus()
            .isBadRequest

        assertEquals("SKIPPED", topk.sweepStatus("dropper", "apple", "fruit", declaration))
    }

    /** A read follows whatever the declaration says now, so the row left behind needs a scan. */
    @Test
    fun `pointing the declaration at another rank table leaves the rows already written behind`() {
        val declaration = topk.declare(DAY.copy(database = "topk_moverank"))

        topk.buy("rank_mover", "apple", at = PURCHASED_AT, declaration = declaration)

        val moved = declaration.copy(rank = RANK2)
        topk.createRankTable(moved)
        topk.alter(moved)
        topk.buy("rank_mover", "apple", at = PURCHASED_AT, declaration = declaration)

        assertEquals(2L, topk.metric("rank_mover", "apple", declaration = moved))
        assertEquals(listOf(1L), topk.rankRows(declaration, "rank_mover", "fruit"))
    }

    @Test
    fun `two top-Ks on one group both get written`() {
        val twotopk = topk.declare(DAY.copy(database = "topk_two", secondTopk = true))

        topk.buy("two_topk", "apple", at = PURCHASED_AT, declaration = twotopk)

        assertEquals(1L, topk.metric("two_topk", "apple", declaration = twotopk))
        assertEquals(1L, topk.metric("two_topk", "apple", topk = TOPK2, declaration = twotopk))
    }

    @Test
    fun `two groups each declaring a top-K both get written`() {
        val twogroups = topk.declare(DAY.copy(database = "topk_groups", secondGroup = true))

        topk.buy("two_groups", "apple", at = PURCHASED_AT, declaration = twogroups)

        assertEquals(1L, topk.metric("two_groups", "apple", declaration = twogroups))
        assertEquals(1L, topk.metric("two_groups", "apple", dimensionValues = null, topk = TOPK2, declaration = twogroups))
    }

    @Test
    fun `a per-entity and a global ranking on one group are both written`() {
        val globalplus = topk.declare(DAY.copy(database = "topk_globalplus", direction = "IN", secondEntity = "__GLOBAL__"))

        topk.buy("alice", "apple", at = PURCHASED_AT, declaration = globalplus)
        topk.buy("bob", "apple", at = PURCHASED_AT, declaration = globalplus)

        assertEquals(2L, topk.metric("apple", "apple", declaration = globalplus))
        assertEquals(2L, topk.metric("whoever", "apple", topk = TOPK2, declaration = globalplus))
    }

    @Test
    fun `an IN group ranks the buyers of an item`() {
        val incoming = topk.declare(DAY.copy(database = "topk_in", direction = "IN", entity = "target", dimension = "source"))

        topk.buy("in_buyer", "apple", at = PURCHASED_AT, declaration = incoming)

        assertEquals(1L, topk.metric("apple", "in_buyer", declaration = incoming))
    }

    /** What a declaration's `dimension` puts in a ranking, and what is left over to split it by. */
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #    |                                               | decl                  | brand | value | split
          #----|-----------------------------------------------|-----------------------|-------|-------|------
          - 51 | a group with nothing to split by keeps one     | NO_SPLIT              | ~     | apple | ~
          - 52 | a dimension that is a plain property ranks it  | BY_BRAND              | acme  | acme  | fruit
          - 53 | a dimension may be written with an underscore  | UNDERSCORED_DIMENSION | ~     | apple | fruit
        """,
    )
    fun `a declaration decides what its ranking holds`(
        case: Int,
        name: String,
        decl: Preset,
        brand: String?,
        value: String,
        split: String?,
    ) {
        val declaration = topk.declare(decl)
        val buyer = "buyer_$case"

        topk.buy(buyer, "apple", at = PURCHASED_AT, brand = brand.orEmpty(), declaration = declaration)

        assertEquals(1L, topk.metric(buyer, value, dimensionValues = split, declaration = declaration), name)
    }

    /** The same boundary as a day bucket, on the other shapes a bucket can take. */
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #    |                                                   | decl               | bought               | refreshed            | metric
          #----|---------------------------------------------------|--------------------|----------------------|----------------------|-------
          - 55 | an hour bucket still counts at the window's length | PER_ENTITY_BY_HOUR | 2026-01-01T14:32:00Z | 2026-01-02T14:32:00Z | 1
          - 56 | and an hour after that it is out                   | PER_ENTITY_BY_HOUR | 2026-01-01T14:32:00Z | 2026-01-02T15:00:00Z | 0
          - 57 | a bucket in another timezone leaves on its own day | IN_SEOUL           | 2026-01-01T14:32:00Z | 2027-01-01T14:32:00Z | 1
        """,
    )
    fun `a purchase leaves the window one bucket after its own length, whatever the bucket`(
        case: Int,
        name: String,
        decl: Preset,
        bought: String,
        refreshed: String,
        metric: Long,
    ) {
        val declaration = topk.declare(decl)
        val buyer = "buyer_$case"

        topk.buy(buyer, "apple", at = bought, declaration = declaration)

        topk.now(refreshed)
        topk.refresh(buyer, "apple", declaration = declaration)

        assertEquals(metric, topk.metric(buyer, "apple", declaration = declaration), name)
    }

    @Test
    fun `an upper bound of now plus a day still holds today`() {
        val upper = topk.declare(DAY.copy(database = "topk_upper", window = "purchasedAt:bt:now-365d,now+1d"))

        topk.buy("upper_buyer", "apple", at = PURCHASED_AT, declaration = upper)

        assertEquals(1L, topk.metric("upper_buyer", "apple", declaration = upper))
    }

    /**
     * Two purchases of one ranking in a single request are recomputed at the same time, and each writes
     * what it read. Whichever lands last is what stands, so the count can fall short until something
     * recomputes it again — which is what the refresh at the end is for.
     */
    @Test
    fun `two aggregations of one ranking in a single request settle on the next recompute`() {
        val day = topk.declare(DAY)

        topk.buyTwiceInOneRequest("bulk_buyer", "apple", at = PURCHASED_AT, declaration = day)

        topk.refresh("bulk_buyer", "apple", declaration = day)

        assertEquals(2L, topk.metric("bulk_buyer", "apple", declaration = day))
    }

    /**
     * An operator can ask for a refresh that does not line up with the window, and the due time follows
     * what was asked for rather than what the window would imply.
     */
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #    |                                                   | decl                    | due
          #----|---------------------------------------------------|-------------------------|----------------------
          - 70 | a refresh shorter than the window falls due early  | REFRESH_EVERY_DAY       | 2026-01-03T00:00:00Z
          - 71 | a longer one falls due after the purchase left     | REFRESH_EVERY_TWO_YEARS | 2028-01-02T00:00:00Z
        """,
    )
    fun `a refresh falls due after what the declaration asked for`(
        case: Int,
        name: String,
        decl: Preset,
        due: String,
    ) {
        val declaration = topk.declare(decl)
        val buyer = "buyer_$case"

        topk.buy(buyer, "apple", at = PURCHASED_AT, declaration = declaration)

        assertEquals(listOf(due), topk.refreshAt(buyer, declaration), name)
    }
}
