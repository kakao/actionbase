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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AliasControllerTest : E2ETestBase() {
    private val db = "v3-alias-test-db"
    private val table = "v3-alias-target-table"
    private val alias = "v3-test-alias"
    private val baseUri = "/graph/v3/databases/$db/aliases"

    @BeforeAll
    fun setup() {
        // Create database
        client
            .post()
            .uri("/graph/v3/databases/$db")
            .bodyValue(DatabaseCreateRequest(db, "test db"))
            .exchange()
            .expectStatus()
            .isOk

        // Create table (alias target)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table")
            .bodyValue(tableRequest())
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @Order(1)
    fun `list aliases - empty`() {
        client
            .get()
            .uri(baseUri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(AliasDescriptor::class.java)
            .hasSize(0)
    }

    @Test
    @Order(2)
    fun `create alias`() {
        client
            .post()
            .uri("$baseUri/$alias")
            .bodyValue(AliasCreateRequest(table, "test alias"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .value {
                assertThat(it.alias).isEqualTo(alias)
                assertThat(it.table).isEqualTo(table)
            }
    }

    @Test
    @Order(3)
    fun `get alias`() {
        client
            .get()
            .uri("$baseUri/$alias")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .value { assertThat(it.alias).isEqualTo(alias) }
    }

    @Test
    @Order(4)
    fun `list aliases - has one`() {
        client
            .get()
            .uri(baseUri)
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
            .uri("$baseUri/non-existent")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @Order(6)
    fun `deactivate alias`() {
        client
            .put()
            .uri("$baseUri/$alias")
            .bodyValue(AliasUpdateRequest(active = false))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(AliasDescriptor::class.java)
            .value { assertThat(it.active).isFalse() }
    }

    @Test
    @Order(7)
    fun `delete alias`() {
        // Delete returns 404 because entity no longer exists after deletion
        client
            .delete()
            .uri("$baseUri/$alias")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @Order(8)
    fun `list aliases after delete - empty`() {
        client
            .get()
            .uri(baseUri)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(AliasDescriptor::class.java)
            .hasSize(0)
    }

    private fun tableRequest() =
        TableCreateRequest(
            schema =
                ModelSchema.Edge(
                    source = Field(PrimitiveType.STRING, "src"),
                    target = Field(PrimitiveType.STRING, "tgt"),
                    properties = emptyList(),
                    direction = DirectionType.OUT,
                ),
            storage = Storage.HBase("alias-test-hbase-table"),
            mode = MutationMode.SYNC,
            comment = "target table",
        )
}
