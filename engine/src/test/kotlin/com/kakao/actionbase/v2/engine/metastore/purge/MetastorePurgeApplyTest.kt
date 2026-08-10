package com.kakao.actionbase.v2.engine.metastore.purge

import com.kakao.actionbase.v2.core.code.HashEdgeValue
import com.kakao.actionbase.v2.core.code.StringKeyFieldValueEdgeEncoder
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Active

import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Delete and restore, against a real table. */
class MetastorePurgeApplyTest {
    private val encoder = StringKeyFieldValueEdgeEncoder()
    private val table = "kc_graph_metadata"
    private val old = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val cutoff = LocalDateTime.of(2026, 6, 1, 0, 0)

    private lateinit var connections: () -> Connection
    private lateinit var purge: MetastorePurge

    @BeforeEach
    fun setUp() {
        val url = "jdbc:h2:mem:purge-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL"
        connections = { DriverManager.getConnection(url, "", "") }
        connections().use { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE $table (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        k VARCHAR(512) NOT NULL UNIQUE,
                        v TEXT NOT NULL,
                        created_at DATETIME(6) NOT NULL,
                        created_by VARCHAR(256) NOT NULL,
                        modified_at DATETIME(6) NOT NULL,
                        modified_by VARCHAR(256) NOT NULL,
                        update_ts DATETIME(6) NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
        purge = MetastorePurge(MetastoreTarget("test", url, table), connections)
    }

    private fun keyOf(name: String): String {
        val encoded = encoder.encodeHashEdgeKey(Edge(0L, SERVICE, name), LABEL_ID)
        return encoded.field?.let { "${encoded.key}:$it" } ?: encoded.key
    }

    private fun valueOf(
        active: Active,
        ts: Long = 1L,
    ) = encoder.encodeHashEdgeValue(HashEdgeValue.from(active, ts, emptyMap(), null, null))

    private fun write(
        k: String,
        v: String,
    ) {
        connections().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO $table (k, v, created_at, created_by, modified_at, modified_by, update_ts) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).use {
                    it.setString(1, k)
                    it.setString(2, v)
                    it.setObject(3, old)
                    it.setString(4, "writer")
                    it.setObject(5, old)
                    it.setString(6, "writer")
                    it.setObject(7, old)
                    it.executeUpdate()
                }
        }
    }

    private fun replaceValue(
        k: String,
        v: String,
    ) {
        connections().use { connection ->
            connection.prepareStatement("UPDATE $table SET v = ? WHERE k = ?").use {
                it.setString(1, v)
                it.setString(2, k)
                it.executeUpdate()
            }
        }
    }

    private fun rowCount(): Int =
        connections().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use {
                    it.next()
                    it.getInt(1)
                }
            }
        }

    private fun candidates() = purge.scan(SERVICE, cutoff, maxRows = 100, maxScan = 1_000).rows

    @Test
    fun `delete removes exactly the rows it was given`() {
        write(keyOf("gone"), valueOf(Active.INACTIVE))
        write(keyOf("also-gone"), valueOf(Active.INACTIVE))
        write(keyOf("live"), valueOf(Active.ACTIVE))

        val outcome = purge.delete(candidates())

        assertEquals(2, outcome.requested)
        assertEquals(2, outcome.applied)
        assertEquals(emptyList<SkippedRow>(), outcome.skipped)
        assertEquals(1, rowCount())
    }

    @Test
    fun `a tombstone that came back to life since it was listed survives`() {
        val k = keyOf("resurrected")
        write(k, valueOf(Active.INACTIVE))
        val listed = candidates()

        replaceValue(k, valueOf(Active.ACTIVE))
        val outcome = purge.delete(listed)

        assertEquals(0, outcome.applied)
        assertEquals(listOf(SkippedRow(k, SkipReason.CHANGED)), outcome.skipped)
        assertEquals(1, rowCount())
    }

    @Test
    fun `repeating a delete after a lost response changes nothing`() {
        write(keyOf("gone"), valueOf(Active.INACTIVE))
        val listed = candidates()
        purge.delete(listed)

        val again = purge.delete(listed)

        assertEquals(0, again.applied)
        assertEquals(listOf(SkipReason.ABSENT), again.skipped.map { it.reason })
        assertEquals(0, rowCount())
    }

    @Test
    fun `restore writes the rows back as they were`() {
        val k = keyOf("gone")
        write(k, valueOf(Active.INACTIVE))
        val listed = candidates()
        purge.delete(listed)

        val outcome = purge.restore(listed)

        assertEquals(1, outcome.applied)
        assertEquals(emptyList<SkippedRow>(), outcome.skipped)

        val restored = candidates().single()
        assertEquals(listed.single(), restored)
    }

    @Test
    fun `restore will not write over a key taken since the purge`() {
        val k = keyOf("gone")
        write(k, valueOf(Active.INACTIVE))
        val listed = candidates()
        purge.delete(listed)
        write(k, valueOf(Active.ACTIVE, ts = 99L))

        val outcome = purge.restore(listed)

        assertEquals(0, outcome.applied)
        assertEquals(listOf(SkippedRow(k, SkipReason.PRESENT)), outcome.skipped)
        assertEquals(emptyList<PurgeRow>(), candidates(), "the live row is still live")
    }

    @Test
    fun `a partial restore reports both sides`() {
        val taken = keyOf("taken")
        val free = keyOf("free")
        write(taken, valueOf(Active.INACTIVE))
        write(free, valueOf(Active.INACTIVE))
        val listed = candidates()
        purge.delete(listed)
        write(taken, valueOf(Active.ACTIVE, ts = 99L))

        val outcome = purge.restore(listed)

        assertEquals(2, outcome.requested)
        assertEquals(1, outcome.applied)
        assertEquals(listOf(SkippedRow(taken, SkipReason.PRESENT)), outcome.skipped)
    }

    private companion object {
        const val SERVICE = "prod:wish"
        const val LABEL_ID = 7
    }
}
