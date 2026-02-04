package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.Storage
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TableControllerTest : E2ETestBase() {
    private val testDatabase = "v3-table-test-db"
    private val testTable = "v3-test-table"

    @BeforeAll
    fun setupDatabase() {
        val request =
            DatabaseCreateRequest(
                database = testDatabase,
                comment = "test database for table api",
            )
        client
            .post()
            .uri("/graph/v3/databases/$testDatabase")
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @Order(1)
    fun `list tables - empty`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/tables")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(TableDescriptor.Edge::class.java)
            .hasSize(0)
    }

    @Test
    @Order(2)
    fun `create table`() {
        val request =
            TableCreateRequest(
                schema =
                    ModelSchema.Edge(
                        source = Field(PrimitiveType.STRING, "source vertex"),
                        target = Field(PrimitiveType.STRING, "target vertex"),
                        properties =
                            listOf(
                                StructField("score", PrimitiveType.INT, "score field", true),
                            ),
                        direction = DirectionType.OUT,
                    ),
                storage = Storage.HBase("test-hbase-table"),
                mode = MutationMode.SYNC,
                comment = "test table for v3 api",
            )

        client
            .post()
            .uri("/graph/v3/databases/$testDatabase/tables/$testTable")
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
                assert(body.table == testTable)
                assert(body.comment == "test table for v3 api")
                assert(body.mode == MutationMode.SYNC)
            }
    }

    @Test
    @Order(3)
    fun `get table`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/tables/$testTable")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(TableDescriptor.Edge::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
                assert(body.table == testTable)
            }
    }

    @Test
    @Order(4)
    fun `list tables - has one`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/tables")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(TableDescriptor.Edge::class.java)
            .hasSize(1)
    }

    @Test
    @Order(5)
    fun `get non-existent table returns 404`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/tables/non-existent-table")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
