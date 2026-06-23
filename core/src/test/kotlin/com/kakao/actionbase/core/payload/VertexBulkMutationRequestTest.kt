package com.kakao.actionbase.core.payload

import com.kakao.actionbase.core.Constants.VERTEX_MARKER
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.core.vertex.Vertex
import com.kakao.actionbase.core.vertex.payload.VertexBulkMutationRequest

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class VertexBulkMutationRequestTest {
    private val schema =
        ModelSchema.Vertex(
            id = Field(PrimitiveType.STRING, "user id"),
            properties =
                listOf(
                    StructField(name = "name", type = PrimitiveType.STRING, comment = "user name", nullable = false),
                    StructField(name = "age", type = PrimitiveType.LONG, comment = "user age", nullable = true),
                ),
        )

    private fun item(
        type: EventType,
        id: Any = "user1",
        properties: Map<String, Any?> = emptyMap(),
    ) = VertexBulkMutationRequest.MutationItem(
        type = type,
        vertex = Vertex(id = id, version = 1L, properties = properties),
    )

    @Test
    fun `createEvent sets source=id and target=VERTEX_MARKER`() {
        val event = item(EventType.INSERT, id = "user1", properties = mapOf("name" to "Alice")).createEvent(schema) as EdgeEvent
        assertEquals("user1", event.source)
        assertEquals(VERTEX_MARKER, event.target)
    }

    @Test
    fun `INSERT with all required fields succeeds`() {
        val event = item(EventType.INSERT, properties = mapOf("name" to "Alice")).createEvent(schema)
        assertEquals("Alice", event.event.properties["name"])
    }

    @Test
    fun `INSERT missing non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.INSERT, properties = mapOf("age" to 20L)).createEvent(schema)
        }
    }

    @Test
    fun `INSERT with explicit null for non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.INSERT, properties = mapOf("name" to null)).createEvent(schema)
        }
    }

    @Test
    fun `UPDATE missing non-nullable field succeeds (keeps existing value)`() {
        val event = item(EventType.UPDATE, properties = mapOf("age" to 21L)).createEvent(schema)
        assertTrue(event.event.properties.containsKey("age"))
    }

    @Test
    fun `UPDATE with explicit null for non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.UPDATE, properties = mapOf("name" to null)).createEvent(schema)
        }
    }

    @Test
    fun `DELETE missing non-nullable field succeeds (tombstone)`() {
        val event = item(EventType.DELETE, properties = emptyMap()).createEvent(schema)
        assertTrue(event.event.properties.isEmpty())
    }

    @Test
    fun `nullable field may be absent or explicitly null`() {
        val absentEvent = item(EventType.INSERT, properties = mapOf("name" to "Alice")).createEvent(schema)
        assertTrue(!absentEvent.event.properties.containsKey("age"))

        val nullEvent = item(EventType.INSERT, properties = mapOf("name" to "Alice", "age" to null)).createEvent(schema)
        assertNull(nullEvent.event.properties["age"])
    }

    @Test
    fun `createEvent rejects wrong schema type`() {
        val edgeSchema =
            ModelSchema.Edge(
                source = Field(PrimitiveType.STRING, "src"),
                target = Field(PrimitiveType.STRING, "tgt"),
                direction = com.kakao.actionbase.core.metadata.common.DirectionType.OUT,
            )
        assertFailsWith<IllegalArgumentException> {
            item(EventType.INSERT, properties = mapOf("name" to "Alice")).createEvent(edgeSchema)
        }
    }
}
