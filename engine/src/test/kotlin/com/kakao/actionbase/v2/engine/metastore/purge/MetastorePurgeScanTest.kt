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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Drives the scan against a real table rather than a mock. Rows are written with the encoder the
 * metastore itself uses, so the test also pins that the purge reads what production writes - which
 * is the claim worth holding onto, given it reads only the front of each encoding.
 */
class MetastorePurgeScanTest {
    private val encoder = StringKeyFieldValueEdgeEncoder()
    private val table = "kc_graph_metadata"
    private lateinit var url: String
    private lateinit var connections: () -> Connection
    private lateinit var purge: MetastorePurge

    private val old = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val recent = LocalDateTime.of(2026, 8, 1, 0, 0)
    private val cutoff = LocalDateTime.of(2026, 6, 1, 0, 0)

    @BeforeEach
    fun setUp() {
        url = "jdbc:h2:mem:purge-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL"
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

    /** Mirrors how `JdbcHashLabel` stores a row: `key` alone, or `key:field` when there is a field. */
    private fun write(
        service: String,
        name: String,
        active: Active,
        updateTs: LocalDateTime = old,
    ) {
        val edge = Edge(0L, service, name)
        val encodedKey = encoder.encodeHashEdgeKey(edge, LABEL_ID)
        val k = encodedKey.field?.let { "${encodedKey.key}:$it" } ?: encodedKey.key
        val v = encoder.encodeHashEdgeValue(HashEdgeValue.from(active, 1L, emptyMap(), null, null))
        insert(k, v, updateTs)
    }

    private fun insert(
        k: String,
        v: String,
        updateTs: LocalDateTime,
    ) {
        connections().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO $table (k, v, created_at, created_by, modified_at, modified_by, update_ts) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).use {
                    it.setString(1, k)
                    it.setString(2, v)
                    it.setObject(3, old)
                    it.setString(4, "test")
                    it.setObject(5, updateTs)
                    it.setString(6, "test")
                    it.setObject(7, updateTs)
                    it.executeUpdate()
                }
        }
    }

    private fun scan(
        service: String = SERVICE,
        maxRows: Int = 100,
        maxScan: Int = 1_000,
        cursor: Long = 0,
    ) = purge.scan(service, cutoff, maxRows, maxScan, cursor)

    @Test
    fun `an inactive row of the requested service is a candidate`() {
        write(SERVICE, "gone", Active.INACTIVE)

        val scan = scan()

        assertEquals(1, scan.rows.size)
        assertEquals(emptyList<UndecodableKey>(), scan.undecodable)
    }

    @Test
    fun `an active row is left alone, which is the whole point`() {
        write(SERVICE, "live", Active.ACTIVE)

        assertEquals(emptyList<PurgeRow>(), scan().rows)
    }

    @Test
    fun `another service's tombstone is out of scope`() {
        write("other-service", "gone", Active.INACTIVE)

        assertEquals(emptyList<PurgeRow>(), scan().rows)
    }

    @Test
    fun `a tombstone settled after the cutoff is too young to purge`() {
        write(SERVICE, "just-deleted", Active.INACTIVE, updateTs = recent)

        assertEquals(emptyList<PurgeRow>(), scan().rows)
    }

    @Test
    fun `a row that cannot be read is reported and never purged`() {
        insert("this-is-not-an-encoded-key", "neither-is-this", old)

        val scan = scan()

        assertEquals(emptyList<PurgeRow>(), scan.rows)
        assertEquals(1, scan.undecodable.size)
        assertEquals("this-is-not-an-encoded-key", scan.undecodable.first().k)
    }

    @Test
    fun `the audit columns come back so a restore can write the row as it was`() {
        write(SERVICE, "gone", Active.INACTIVE)

        val row = scan().rows.single()

        assertEquals("test", row.createdBy)
        assertEquals(old, row.createdAt)
        assertEquals(old, row.updateTs)
    }

    @Test
    fun `maxRows caps the page and leaves a cursor to resume from`() {
        repeat(5) { write(SERVICE, "gone-$it", Active.INACTIVE) }

        val first = scan(maxRows = 2)

        assertEquals(2, first.rows.size)
        assertTrue(first.nextCursor != null)

        val second = scan(maxRows = 10, cursor = first.nextCursor!!)
        assertEquals(3, second.rows.size)
    }

    @Test
    fun `an exhausted table reports no cursor`() {
        write(SERVICE, "gone", Active.INACTIVE)

        assertNull(scan().nextCursor)
    }

    @Test
    fun `maxScan stops the walk when tombstones are sparse`() {
        repeat(10) { write(SERVICE, "live-$it", Active.ACTIVE) }

        val scan = scan(maxRows = 5, maxScan = 5)

        assertEquals(emptyList<PurgeRow>(), scan.rows)
        assertEquals(5, scan.scanned)
        assertTrue(scan.nextCursor != null, "a capped walk has to say where to resume")
    }

    @Test
    fun `scanned reports how far the walk went, which is what shows tombstone density`() {
        repeat(3) { write(SERVICE, "live-$it", Active.ACTIVE) }
        write(SERVICE, "gone", Active.INACTIVE)

        val scan = scan()

        assertEquals(4, scan.scanned)
        assertEquals(1, scan.rows.size)
    }

    private companion object {
        const val SERVICE = "prod:wish"
        const val LABEL_ID = 7
    }
}
