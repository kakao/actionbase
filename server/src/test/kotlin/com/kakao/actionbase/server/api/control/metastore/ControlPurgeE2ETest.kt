package com.kakao.actionbase.server.api.control.metastore

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.v2.core.code.HashEdgeValue
import com.kakao.actionbase.v2.core.code.StringKeyFieldValueEdgeEncoder
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Active

import java.sql.DriverManager
import java.time.LocalDateTime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType

/**
 * The purge over http, against a real metastore table.
 *
 * The point being pinned is that the document round-trips: whatever `candidates` returns is posted
 * back to `execute` and `restore` byte for byte, with no client-side reshaping. If that ever stops
 * holding, the file an operator saved stops being usable as a backup.
 */
@SpringBootTest(
    properties = [
        "actionbase.role=CONTROL",
        "actionbase.control.tenants.alpha.env=prod",
        "actionbase.control.tenants.alpha.namespace=ab_alpha",
        "actionbase.control.tenants.alpha.active-url=http://127.0.0.1:1",
        "actionbase.control.metastores.alpha.url=$METASTORE_URL",
        "actionbase.control.metastores.alpha.table=kc_graph_metadata",
    ],
)
class ControlPurgeE2ETest : E2ETestBase() {
    private val encoder = StringKeyFieldValueEdgeEncoder()
    private val old: LocalDateTime = LocalDateTime.now().minusYears(1)

    @BeforeEach
    fun resetTable() {
        DriverManager.getConnection(METASTORE_URL, "", "").use { connection ->
            connection.createStatement().use {
                it.execute("DROP TABLE IF EXISTS kc_graph_metadata")
                it.execute(
                    """
                    CREATE TABLE kc_graph_metadata (
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
    }

    private fun write(
        name: String,
        active: Active,
    ) {
        val encoded = encoder.encodeHashEdgeKey(Edge(0L, SERVICE, name), LABEL_ID)
        val k = encoded.field?.let { "${encoded.key}:$it" } ?: encoded.key
        val v = encoder.encodeHashEdgeValue(HashEdgeValue.from(active, 1L, emptyMap(), null, null))
        DriverManager.getConnection(METASTORE_URL, "", "").use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO kc_graph_metadata (k, v, created_at, created_by, modified_at, modified_by, update_ts) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
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

    private fun rowCount(): Int =
        DriverManager.getConnection(METASTORE_URL, "", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM kc_graph_metadata").use {
                    it.next()
                    it.getInt(1)
                }
            }
        }

    /** The response body as it came off the wire, which is what gets posted back. */
    private fun candidates(): String =
        client
            .post()
            .uri("/control/metastore/purge/candidates")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"metastore":"alpha","service":"$SERVICE"}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

    private fun post(
        path: String,
        body: String,
    ) = client
        .post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()

    @Test
    fun `candidates lists the tombstones with their contents and leaves the table alone`() {
        write("gone", Active.INACTIVE)
        write("live", Active.ACTIVE)

        post("/control/metastore/purge/candidates", """{"metastore":"alpha","service":"$SERVICE"}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.rows.length()")
            .isEqualTo(1)
            .jsonPath("$.rows[0].v")
            .exists()
            .jsonPath("$.table")
            .isEqualTo("kc_graph_metadata")
            .jsonPath("$.metastore")
            .value<String> { assertTrue(it.startsWith("jdbc:h2:"), it) }

        assertEquals(2, rowCount())
    }

    @Test
    fun `the candidates document is posted back verbatim to delete and to restore`() {
        write("gone", Active.INACTIVE)
        write("live", Active.ACTIVE)
        val document = candidates()

        post("/control/metastore/purge/execute", document)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.applied")
            .isEqualTo(1)
            .jsonPath("$.skipped.length()")
            .isEqualTo(0)
        assertEquals(1, rowCount(), "only the tombstone went")

        post("/control/metastore/purge/restore", document)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.applied")
            .isEqualTo(1)
        assertEquals(2, rowCount(), "the same document put it back")
    }

    @Test
    fun `repeating execute after a lost response changes nothing`() {
        write("gone", Active.INACTIVE)
        val document = candidates()
        post("/control/metastore/purge/execute", document).expectStatus().isOk

        post("/control/metastore/purge/execute", document)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.applied")
            .isEqualTo(0)
            .jsonPath("$.skipped[0].reason")
            .isEqualTo("ABSENT")
        assertEquals(0, rowCount())
    }

    @Test
    fun `a row that cannot be read is reported and never deleted`() {
        DriverManager.getConnection(METASTORE_URL, "", "").use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO kc_graph_metadata (k, v, created_at, created_by, modified_at, modified_by, update_ts) " +
                        "VALUES ('not-an-encoded-key', 'nor-this', ?, 'writer', ?, 'writer', ?)",
                ).use {
                    it.setObject(1, old)
                    it.setObject(2, old)
                    it.setObject(3, old)
                    it.executeUpdate()
                }
        }

        post("/control/metastore/purge/candidates", """{"metastore":"alpha","service":"$SERVICE"}""")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.rows.length()")
            .isEqualTo(0)
            .jsonPath("$.undecodable[0].k")
            .isEqualTo("not-an-encoded-key")

        assertEquals(1, rowCount())
    }

    @Test
    fun `an unconfigured metastore name is refused`() {
        post("/control/metastore/purge/candidates", """{"metastore":"nope","service":"$SERVICE"}""")
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `a document with no rows applies nothing rather than failing`() {
        write("live", Active.ACTIVE)
        val document = candidates()

        post("/control/metastore/purge/execute", document)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.requested")
            .isEqualTo(0)
            .jsonPath("$.applied")
            .isEqualTo(0)
        assertEquals(1, rowCount())
    }

    @Test
    fun `an empty document still has to name a metastore this instance serves`() {
        val document = candidates().replace(METASTORE_URL, "jdbc:mysql://somewhere-else.example.net:3306/graph")

        post("/control/metastore/purge/execute", document).expectStatus().isBadRequest
    }

    @Test
    fun `a document naming a metastore this instance does not serve is refused`() {
        write("gone", Active.INACTIVE)
        val document = candidates().replace(METASTORE_URL, "jdbc:mysql://somewhere-else.example.net:3306/graph")

        post("/control/metastore/purge/execute", document).expectStatus().isBadRequest
        assertEquals(1, rowCount(), "nothing was touched")
    }

    private companion object {
        const val SERVICE = "prod:wish"
        const val LABEL_ID = 7
    }
}

private const val METASTORE_URL = "jdbc:h2:mem:purge-e2e;DB_CLOSE_DELAY=-1;MODE=MySQL"
