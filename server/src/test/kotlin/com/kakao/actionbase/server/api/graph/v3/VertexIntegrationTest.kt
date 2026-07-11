package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertexIntegrationTest : E2ETestBase() {
    private val db = "vertex_db"
    private val vertexTable = "users"
    private val longIdTable = "users_long"

    @BeforeAll
    fun setup() {
        // 1. Create database
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "vertex integration test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        // 2. Create vertex table
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$vertexTable",
                  "schema": {
                    "type": "VERTEX",
                    "id": {"type": "string", "comment": "user unique key"},
                    "properties": [
                      {"name": "name", "type": "string", "comment": "user name"},
                      {"name": "age", "type": "long", "comment": "user age", "nullable": true}
                    ]
                  },
                  "storage": "datastore://vertex_ns/users",
                  "mode": "SYNC",
                  "comment": "users vertex table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // 3. Create vertex table with LONG id (regression: id must round-trip as Long)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$longIdTable",
                  "schema": {
                    "type": "VERTEX",
                    "id": {"type": "long", "comment": "numeric user id"},
                    "properties": [
                      {"name": "name", "type": "string", "comment": "user name"}
                    ]
                  },
                  "storage": "datastore://vertex_ns/users_long",
                  "mode": "SYNC",
                  "comment": "users vertex table with numeric id"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `test vertex mutations and get queries`() {
        // 1. Insert vertices
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {
                      "type": "INSERT",
                      "vertex": {
                        "version": 1,
                        "id": "user1",
                        "properties": {"name": "Alice", "age": 20}
                      }
                    },
                    {
                      "type": "INSERT",
                      "vertex": {
                        "version": 1,
                        "id": "user2",
                        "properties": {"name": "Bob", "age": 25}
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(2)
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
            .jsonPath("$.results[0].id")
            .isEqualTo("user1")
            .jsonPath("$.results[1].status")
            .isEqualTo("CREATED")
            .jsonPath("$.results[1].id")
            .isEqualTo("user2")

        // 2. Get vertices (Multi-Get)
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices/get?id=user1,user2,user3")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.vertices.length()")
            .isEqualTo(2)
            .jsonPath("$.vertices[0].id")
            .isEqualTo("user1")
            .jsonPath("$.vertices[0].properties.name")
            .isEqualTo("Alice")
            .jsonPath("$.vertices[0].properties.age")
            .isEqualTo(20)
            .jsonPath("$.vertices[1].id")
            .isEqualTo("user2")
            .jsonPath("$.vertices[1].properties.name")
            .isEqualTo("Bob")
            .jsonPath("$.vertices[1].properties.age")
            .isEqualTo(25)

        // 3. Update vertex
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {
                      "type": "UPDATE",
                      "vertex": {
                        "version": 2,
                        "id": "user1",
                        "properties": {"name": "Alice In Wonderland", "age": 21}
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(1)
            .jsonPath("$.results[0].status")
            .isEqualTo("UPDATED")

        // 4. Get updated vertex
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices/get?id=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.vertices.length()")
            .isEqualTo(1)
            .jsonPath("$.vertices[0].id")
            .isEqualTo("user1")
            .jsonPath("$.vertices[0].properties.name")
            .isEqualTo("Alice In Wonderland")
            .jsonPath("$.vertices[0].properties.age")
            .isEqualTo(21)

        // 5. Delete vertex
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {
                      "type": "DELETE",
                      "vertex": {
                        "version": 3,
                        "id": "user1",
                        "properties": {}
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(1)
            .jsonPath("$.results[0].status")
            .isEqualTo("DELETED")

        // 6. Get deleted vertex (should be empty since active = false)
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$vertexTable/vertices/get?id=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.vertices.length()")
            .isEqualTo(0)
    }

    @Test
    fun `test vertex with LONG id type`() {
        // Insert a vertex with numeric id; the id arrives in JSON as a number, not a string.
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$longIdTable/vertices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "vertex": {"version": 1, "id": 100, "properties": {"name": "Alice"}}},
                    {"type": "INSERT", "vertex": {"version": 1, "id": 200, "properties": {"name": "Bob"}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(2)

        // Multi-Get with numeric ids over query string. Spring binds List<Any> from comma-split.
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$longIdTable/vertices/get?id=100,200,300")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.vertices.length()")
            .isEqualTo(2)
            .jsonPath("$.vertices[0].id")
            .isEqualTo(100)
            .jsonPath("$.vertices[1].id")
            .isEqualTo(200)
    }
}
