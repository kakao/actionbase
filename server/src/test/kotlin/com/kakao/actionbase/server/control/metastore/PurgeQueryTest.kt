package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The ceilings a request body cannot talk its way past.
 *
 * `/control` has no authorization yet, so these are the only thing standing between a caller and a
 * page held whole in memory. They are asserted here rather than through the endpoint because the
 * clamp has to hold for every caller, not just the one the E2E test writes.
 */
class PurgeQueryTest {
    private fun query(
        olderThanDays: Long = PurgeQuery.DEFAULT_OLDER_THAN_DAYS,
        maxRows: Int = PurgeQuery.DEFAULT_MAX_ROWS,
        maxScan: Int = PurgeQuery.DEFAULT_MAX_SCAN,
        cursor: Long = 0,
    ) = PurgeQuery(
        metastore = "alpha",
        service = "prod:wish",
        olderThanDays = olderThanDays,
        maxRows = maxRows,
        maxScan = maxScan,
        cursor = cursor,
    )

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - -1      | 1
        - 0       | 1
        - 500     | 500
        - 5000    | 5000
        - 5001    | 5000
        - 1000000 | 5000
        """,
    )
    fun `maxRows is clamped to the ceiling`(
        requested: Int,
        expected: Int,
    ) {
        assertThat(query(maxRows = requested).bounded().maxRows).isEqualTo(expected)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - 50000    | 50000
        - 1000000  | 1000000
        - 1000001  | 1000000
        - 99999999 | 1000000
        """,
    )
    fun `maxScan is clamped to the ceiling`(
        requested: Int,
        expected: Int,
    ) {
        assertThat(query(maxScan = requested).bounded().maxScan).isEqualTo(expected)
    }

    @Test
    fun `maxScan below maxRows is raised to it, since a walk that short cannot fill the page it was asked for`() {
        val bounded = query(maxRows = 2000, maxScan = 10).bounded()

        assertThat(bounded.maxScan).isEqualTo(2000)
    }

    @Test
    fun `maxScan is raised to the clamped maxRows, not the requested one`() {
        val bounded = query(maxRows = 1000000, maxScan = 1).bounded()

        assertThat(bounded.maxScan).isEqualTo(PurgeQuery.MAX_ROWS_CEILING)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - -1 | 0
        - 0  | 0
        - 30 | 30
        """,
    )
    fun `a negative age would reach past now, so it floors at zero`(
        requested: Long,
        expected: Long,
    ) {
        assertThat(query(olderThanDays = requested).bounded().olderThanDays).isEqualTo(expected)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - -1  | 0
        - 0   | 0
        - 900 | 900
        """,
    )
    fun `a negative cursor floors at zero`(
        requested: Long,
        expected: Long,
    ) {
        assertThat(query(cursor = requested).bounded().cursor).isEqualTo(expected)
    }

    @Test
    fun `a query inside the bounds comes back as it was`() {
        val original = query()

        assertThat(original.bounded()).isEqualTo(original)
    }

    @Test
    fun `clamping leaves the target alone`() {
        val bounded = query(maxRows = 1000000).bounded()

        assertThat(bounded.metastore).isEqualTo("alpha")
        assertThat(bounded.service).isEqualTo("prod:wish")
    }
}
