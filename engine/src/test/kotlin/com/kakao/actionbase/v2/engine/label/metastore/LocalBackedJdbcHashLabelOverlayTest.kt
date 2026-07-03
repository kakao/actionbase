package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import reactor.core.publisher.Mono
import reactor.kotlin.test.test

@DisplayName("LocalBackedJdbcHashLabel — overlay merge policy")
class LocalBackedJdbcHashLabelOverlayTest {
    // allStructType layout: active=0, dir=1, ts=2, src=3, tgt=4, [user fields...]
    // entity.schema.srcIndex = 3, tgtIndex = 4
    private val schema =
        EdgeSchema(
            VertexField(VertexType.STRING),
            VertexField(VertexType.STRING),
            listOf(Field("desc", DataType.STRING, true)),
        )

    // Row in allStructType layout: [active, dir, ts, src, tgt, desc]
    private fun row(
        src: String,
        tgt: String,
        desc: String?,
        active: Boolean = true,
    ) = Row(arrayOf(active, "OUT", 0L, src, tgt, desc))

    private fun frame(rows: List<Row>) = DataFrame(rows, schema.allStructType)

    // count row layout: (src=0, COUNT=1, dir=2)
    private fun countRow(src: String, count: Long) = Row(arrayOf(src, count, "OUT"))
    private fun countFrame(rows: List<Row>) = DataFrame(rows, schema.structType)

    private val entity = mockk<LabelEntity>(relaxed = true)
    private val localLabel = mockk<JdbcHashLabel>(relaxed = true)
    private val globalLabel = mockk<JdbcHashLabel>(relaxed = true)
    private val consolidatedLabel = mockk<HBaseHashLabel>(relaxed = true)

    private lateinit var overlay: LocalBackedJdbcHashLabel

    @BeforeEach
    fun setUp() {
        every { entity.name } returns EntityName("test", "overlay_test")
        every { entity.schema } returns schema
        overlay = LocalBackedJdbcHashLabel(entity, localLabel, globalLabel, consolidatedLabel)
        overlay.useGlobalStore()
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mutate — INSERT/UPDATE: HBase only")
    inner class MutateInsertTest {
        @Test
        fun `INSERT goes to HBase only, MySQL never touched`() {
            every {
                consolidatedLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            } returns Mono.just(emptyList())

            overlay.mutate(emptyList<TraceEdge>(), EdgeOperation.INSERT)
                .test()
                .assertNext { assert(it.isEmpty()) }
                .verifyComplete()

            verify(exactly = 1) {
                consolidatedLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            }
            verify(exactly = 0) {
                globalLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            }
        }
    }

    @Nested
    @DisplayName("mutate — DELETE: HBase + MySQL mirrored")
    inner class MutateDeleteTest {
        @Test
        fun `DELETE is mirrored to MySQL so rows cannot resurface`() {
            val ctx = mockk<CdcContext>(relaxed = true)
            every {
                consolidatedLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            } returns Mono.just(listOf(ctx))
            every {
                globalLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            } returns Mono.just(emptyList())

            overlay.mutate(emptyList<TraceEdge>(), EdgeOperation.DELETE)
                .test()
                .assertNext { assert(it == listOf(ctx)) }
                .verifyComplete()

            verify(exactly = 1) {
                consolidatedLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            }
            verify(exactly = 1) {
                globalLabel.mutate(any<List<TraceEdge>>(), any(), any(), any(), any(), any<() -> StorageOpCollector?>())
            }
        }
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSelf — HBase wins on (src, tgt) dedup")
    inner class GetSelfTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            cases = """
            - name: HBase row shadows MySQL row with the same src/tgt
              hbaseSrc: A
              hbaseTgt: A
              hbaseDesc: hbase
              mysqlSameSrc: A
              mysqlSameTgt: A
              mysqlSameDesc: mysql
              mysqlOnlySrc: B
              mysqlOnlyTgt: B
              mysqlOnlyDesc: mysql-only
              expectedSize: 2
              expectedWinnerDesc: hbase
            """,
        )
        fun `HBase wins on same src-tgt, MySQL-only rows are included`(
            hbaseSrc: String,
            hbaseTgt: String,
            hbaseDesc: String,
            mysqlSameSrc: String,
            mysqlSameTgt: String,
            mysqlSameDesc: String,
            mysqlOnlySrc: String,
            mysqlOnlyTgt: String,
            mysqlOnlyDesc: String,
            expectedSize: Int,
            expectedWinnerDesc: String,
        ) {
            every { localLabel.getSelf(any(), any()) } returns Mono.just(frame(emptyList()))
            every { consolidatedLabel.getSelf(any(), any()) } returns
                Mono.just(frame(listOf(row(hbaseSrc, hbaseTgt, hbaseDesc))))
            every { globalLabel.getSelf(any(), any()) } returns
                Mono.just(
                    frame(
                        listOf(
                            row(mysqlSameSrc, mysqlSameTgt, mysqlSameDesc),
                            row(mysqlOnlySrc, mysqlOnlyTgt, mysqlOnlyDesc),
                        ),
                    ),
                )

            overlay.getSelf(listOf("A", "B"), emptySet())
                .test()
                .assertNext { result ->
                    assert(result.rows.size == expectedSize) {
                        "expected $expectedSize rows but got ${result.rows.size}"
                    }
                    // allStructType: active=0, dir=1, ts=2, src=3, tgt=4, desc=5
                    val winner = result.rows.first { it[3] == hbaseSrc && it[4] == hbaseTgt }
                    assert(winner[5] == expectedWinnerDesc)
                }
                .verifyComplete()
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            cases = """
            - name: soft-deleted HBase row suppresses MySQL row with the same key
              src: A
              tgt: A
            """,
        )
        fun `soft-deleted HBase row suppresses MySQL row`(src: String, tgt: String) {
            every { localLabel.getSelf(any(), any()) } returns Mono.just(frame(emptyList()))
            every { consolidatedLabel.getSelf(any(), any()) } returns
                Mono.just(frame(listOf(row(src, tgt, "hbase-deleted", active = false))))
            every { globalLabel.getSelf(any(), any()) } returns
                Mono.just(frame(listOf(row(src, tgt, "mysql-live"))))

            overlay.getSelf(listOf(src), emptySet())
                .test()
                .assertNext { result ->
                    assert(result.rows.size == 1)
                    assert(result.rows[0][5] == "hbase-deleted") {
                        "MySQL row should be suppressed by soft-deleted HBase row"
                    }
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("count — HBase wins on src dedup")
    inner class CountTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            cases = """
            - name: HBase count wins; MySQL-only src is included
              hbaseSrc: A
              hbaseCount: 5
              mysqlSameSrc: A
              mysqlSameCount: 3
              mysqlOnlySrc: B
              mysqlOnlyCount: 2
              expectedRows: 2
              expectedCountForA: 5
            """,
        )
        fun `HBase count wins, MySQL-only src is included`(
            hbaseSrc: String,
            hbaseCount: Long,
            mysqlSameSrc: String,
            mysqlSameCount: Long,
            mysqlOnlySrc: String,
            mysqlOnlyCount: Long,
            expectedRows: Int,
            expectedCountForA: Long,
        ) {
            every { localLabel.count(any<Set<Any>>(), any()) } returns Mono.just(countFrame(emptyList()))
            every { consolidatedLabel.count(any<Set<Any>>(), any()) } returns
                Mono.just(countFrame(listOf(countRow(hbaseSrc, hbaseCount))))
            every { globalLabel.count(any<Set<Any>>(), any()) } returns
                Mono.just(
                    countFrame(
                        listOf(
                            countRow(mysqlSameSrc, mysqlSameCount),
                            countRow(mysqlOnlySrc, mysqlOnlyCount),
                        ),
                    ),
                )

            overlay.count(setOf("A", "B"), Direction.OUT)
                .test()
                .assertNext { result ->
                    assert(result.rows.size == expectedRows)
                    val countA = result.rows.first { it[0] == hbaseSrc }[1] as Long
                    assert(countA == expectedCountForA)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findStaleLockAndClear — delegates to HBase")
    inner class LockTest {
        @Test
        fun `lock operations go to HBase only`() {
            val lockEdge = KeyValue<Any>("key", "val")
            every { consolidatedLabel.findStaleLockAndClear(any(), any()) } returns Mono.empty()

            overlay.findStaleLockAndClear(lockEdge, 5000L)
                .test()
                .verifyComplete()

            verify(exactly = 1) { consolidatedLabel.findStaleLockAndClear(any(), any()) }
            verify(exactly = 0) { globalLabel.findStaleLockAndClear(any(), any()) }
        }
    }
}
