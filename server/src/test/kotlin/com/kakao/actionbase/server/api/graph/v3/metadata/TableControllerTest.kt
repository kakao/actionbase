package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.test.E2ETestBase

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TableControllerTest : E2ETestBase() {
    private val db = "v3-table-test-db"
    private val table = "v3-test-table"
    private val baseUri = "/graph/v3/databases/$db/tables"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases/$db")
            .bodyValue(DatabaseCreateRequest(db, "test db"))
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @Order(1)
    fun `list tables - empty`() {
        client
            .get()
            .uri(baseUri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(TableDescriptor.Edge::class.java)
            .hasSize(0)
    }

    @Test
    @Order(2)
    fun `create table`() {
        client
            .post()
            .uri("$baseUri/$table")
            .bodyValue(tableRequest())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .value {
                assertThat(it.database).isEqualTo(db)
                assertThat(it.table).isEqualTo(table)
            }
    }

    @Test
    @Order(3)
    fun `get table`() {
        client
            .get()
            .uri("$baseUri/$table")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .value { assertThat(it.table).isEqualTo(table) }
    }

    @Test
    @Order(4)
    fun `list tables - has one`() {
        client
            .get()
            .uri(baseUri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(TableDescriptor.Edge::class.java)
            .hasSize(1)
    }

    @Test
    @Order(5)
    fun `update table`() {
        client
            .put()
            .uri("$baseUri/$table")
            .bodyValue(TableUpdateRequest(comment = "updated comment"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .value { assertThat(it.comment).isEqualTo("updated comment") }
    }

    @Test
    @Order(6)
    fun `get non-existent table returns 404`() {
        client
            .get()
            .uri("$baseUri/non-existent")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @Order(7)
    fun `invalid table name returns 400`() {
        client
            .get()
            .uri("$baseUri/123-invalid")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    @Order(8)
    fun `table name with dot returns 400`() {
        client
            .get()
            .uri("$baseUri/table.injection")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    @Order(9)
    fun `deactivate table`() {
        client
            .put()
            .uri("$baseUri/$table")
            .bodyValue(TableUpdateRequest(active = false))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .value { assertThat(it.active).isFalse() }
    }

    @Test
    @Order(10)
    fun `delete table returns 204`() {
        client
            .delete()
            .uri("$baseUri/$table")
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    @Order(11)
    fun `list tables after delete - empty`() {
        client
            .get()
            .uri(baseUri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(TableDescriptor.Edge::class.java)
            .hasSize(0)
    }

    private fun tableRequest() =
        TableCreateRequest(
            schema =
                ModelSchema.Edge(
                    source = Field(PrimitiveType.STRING, "src"),
                    target = Field(PrimitiveType.STRING, "tgt"),
                    properties = listOf(StructField("score", PrimitiveType.INT, "score", true)),
                    direction = DirectionType.OUT,
                ),
            storage = "datastore://hbase/test-hbase-table",
            mode = MutationMode.SYNC,
            comment = "test table",
        )
}
