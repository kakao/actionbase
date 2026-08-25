package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.MutableClock

import java.time.Clock

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType

/**
 * Reads that name something the declaration does not offer. Nobody lives through these — whoever called the
 * API got a parameter wrong — so they are here rather than in [TopkScenarioTest].
 *
 * The line between them is what the caller can be told. A ranking nobody ever wrote reads back empty, since
 * the key is legal and simply has no row; a top-K nobody ever declared is refused outright.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TopkFixture.MovableClock::class)
class TopkApiContractE2ETest : E2ETestBase() {
    @Autowired
    private lateinit var injectedClock: Clock

    private lateinit var topk: TopkFixture

    @BeforeAll
    fun setup() {
        topk = TopkFixture(client, injectedClock as MutableClock)
        topk.createRefreshQueue()
        topk.declare(DAY)
    }

    @Test
    fun `a per-entity ranking read with no entity comes back empty`() {
        topk.buy("alice", "apple", dimensionValues = "contract", at = PURCHASED_AT)

        assertNull(topk.metric("", "apple", dimensionValues = "contract"))
    }

    @Test
    fun `a ranking read without the value it is split by comes back empty`() {
        topk.buy("alice", "pear", dimensionValues = "contract", at = PURCHASED_AT)

        assertNull(topk.metric("alice", "pear", dimensionValues = null))
    }

    @Test
    fun `a top-K the table never declared is refused`() {
        readRefused(DAY, "top_undeclared")
    }

    /** The scenarios read through the query; this is the one place the endpoint answers for itself. */
    @Test
    fun `the ranking endpoint refuses a top-K the table never declared`() {
        client
            .post()
            .uri("/aggregations/v1/databases/${DAY.database}/tables/$TABLE/topks/top_undeclared")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"entity": "alice", "dimensionValues": {"category": "fruit"}}""")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    /** The ranking is written either way; it is the read that needs the index to scan. */
    @Test
    fun `a rank table without the metric index cannot be read back`() {
        val noindex = topk.declare(DAY.copy(database = "topk_noindex", indexedRank = false))

        topk.buy("alice", "plum", at = PURCHASED_AT, declaration = noindex)

        readRefused(noindex, TOPK)
    }

    /** Registering names a top-K without resolving it, so a read like this is refused when it runs. */
    private fun readRefused(
        declaration: Declaration,
        topkName: String,
    ) {
        client
            .post()
            .uri("/graph/v3/query/${topk.preparedRead(declaration, topkName, declaration.split)}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"arguments": {"entity": "alice", "limit": 10, "$DIMENSION_VALUE": "fruit"}}""",
            ).exchange()
            .expectStatus()
            .isBadRequest
    }
}
