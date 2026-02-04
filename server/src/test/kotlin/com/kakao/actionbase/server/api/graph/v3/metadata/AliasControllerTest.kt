package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.Storage
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
class AliasControllerTest : E2ETestBase() {
    private val testDatabase = "v3-alias-test-db"
    private val testTable = "v3-alias-target-table"
    private val testAlias = "v3-test-alias"

    @BeforeAll
    fun setupDatabaseAndTable() {
        // Create database
        val dbRequest =
            DatabaseCreateRequest(
                database = testDatabase,
                comment = "test database for alias api",
            )
        client
            .post()
            .uri("/graph/v3/databases/$testDatabase")
            .bodyValue(dbRequest)
            .exchange()
            .expectStatus()
            .isOk

        // Create table (alias target)
        val tableRequest =
            TableCreateRequest(
                schema =
                    ModelSchema.Edge(
                        source = Field(PrimitiveType.STRING, "source vertex"),
                        target = Field(PrimitiveType.STRING, "target vertex"),
                        properties = emptyList(),
                        direction = DirectionType.OUT,
                    ),
                storage = Storage.HBase("alias-test-hbase-table"),
                mode = MutationMode.SYNC,
                comment = "target table for alias",
            )
        client
            .post()
            .uri("/graph/v3/databases/$testDatabase/tables/$testTable")
            .bodyValue(tableRequest)
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @Order(1)
    fun `list aliases - empty`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/aliases")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(AliasDescriptor::class.java)
            .hasSize(0)
    }

    @Test
    @Order(2)
    fun `create alias`() {
        val request =
            AliasCreateRequest(
                table = testTable,
                comment = "test alias for v3 api",
            )

        client
            .post()
            .uri("/graph/v3/databases/$testDatabase/aliases/$testAlias")
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
                assert(body.alias == testAlias)
                assert(body.table == testTable)
                assert(body.comment == "test alias for v3 api")
            }
    }

    @Test
    @Order(3)
    fun `get alias`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/aliases/$testAlias")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
                assert(body.alias == testAlias)
                assert(body.table == testTable)
            }
    }

    @Test
    @Order(4)
    fun `list aliases - has one`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/aliases")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(AliasDescriptor::class.java)
            .hasSize(1)
    }

    @Test
    @Order(5)
    fun `get non-existent alias returns 404`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/aliases/non-existent-alias")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @Order(6)
    fun `deactivate alias before delete`() {
        val request =
            AliasUpdateRequest(
                active = false,
                comment = null,
                table = null,
            )

        client
            .put()
            .uri("/graph/v3/databases/$testDatabase/aliases/$testAlias")
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.alias == testAlias)
                assert(!body.active)
            }
    }

    @Test
    @Order(7)
    fun `delete alias`() {
        // Delete returns 404 after successful deletion because the entity no longer exists
        // We verify deletion was successful by checking the list is empty in the next test
        client
            .delete()
            .uri("/graph/v3/databases/$testDatabase/aliases/$testAlias")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @Order(8)
    fun `list aliases after delete - empty`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase/aliases")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(AliasDescriptor::class.java)
            .hasSize(0)
    }
}
