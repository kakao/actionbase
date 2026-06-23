package com.kakao.actionbase.core.payload

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.types.PrimitiveType

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class EdgeBulkMutationRequestTest {
    private val schema =
        ModelSchema.Edge(
            source = Field(PrimitiveType.STRING, "source"),
            target = Field(PrimitiveType.STRING, "target"),
            properties =
                listOf(
                    StructField(name = "required", type = PrimitiveType.STRING, comment = "required field", nullable = false),
                    StructField(name = "optional", type = PrimitiveType.STRING, comment = "optional field", nullable = true),
                ),
            direction = DirectionType.OUT,
            indexes = emptyList(),
            groups = emptyList(),
        )

    private fun item(
        type: EventType,
        properties: Map<String, Any?>,
    ) = EdgeBulkMutationRequest.MutationItem(
        type = type,
        edge = Edge(source = "src", target = "tgt", version = 1L, properties = properties),
    )

    @Test
    fun `INSERT with all required fields succeeds`() {
        val event = item(EventType.INSERT, mapOf("required" to "value")).createEvent(schema)
        assertEquals("value", event.event.properties["required"])
    }

    @Test
    fun `INSERT missing non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.INSERT, mapOf("optional" to "value")).createEvent(schema)
        }
    }

    @Test
    fun `INSERT with explicit null for non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.INSERT, mapOf("required" to null)).createEvent(schema)
        }
    }

    @Test
    fun `UPDATE missing non-nullable field succeeds (keeps existing value)`() {
        val event = item(EventType.UPDATE, mapOf("optional" to "value")).createEvent(schema)
        assertTrue(event.event.properties.containsKey("optional"))
    }

    @Test
    fun `UPDATE with explicit null for non-nullable field throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            item(EventType.UPDATE, mapOf("required" to null)).createEvent(schema)
        }
    }

    @Test
    fun `UPDATE with non-null value for non-nullable field succeeds`() {
        val event = item(EventType.UPDATE, mapOf("required" to "new-value")).createEvent(schema)
        assertEquals("new-value", event.event.properties["required"])
    }

    @Test
    fun `DELETE missing non-nullable field succeeds (tombstone)`() {
        val event = item(EventType.DELETE, emptyMap()).createEvent(schema)
        assertTrue(event.event.properties.isEmpty())
    }

    @Test
    fun `nullable field may be absent or null`() {
        val absentEvent = item(EventType.INSERT, mapOf("required" to "value")).createEvent(schema)
        assertTrue(!absentEvent.event.properties.containsKey("optional"))

        val nullEvent = item(EventType.INSERT, mapOf("required" to "value", "optional" to null)).createEvent(schema)
        assertNull(nullEvent.event.properties["optional"])
    }

    @Test
    fun `createEvent should preserve explicit null values in properties`() {
        // given
        val schema =
            ModelSchema.Edge(
                source = Field(PrimitiveType.STRING, "source"),
                target = Field(PrimitiveType.STRING, "target"),
                properties =
                    listOf(
                        StructField(name = "prop1", type = PrimitiveType.STRING, "Property 1", nullable = false),
                        StructField(name = "prop2", type = PrimitiveType.STRING, "Property 2", nullable = true),
                        StructField(name = "prop2", type = PrimitiveType.STRING, "Property 2", nullable = true),
                    ),
                direction = DirectionType.OUT,
                indexes = emptyList(),
                groups = emptyList(),
            )

        val edge =
            Edge(
                source = "sourceId",
                target = "targetId",
                version = 1L,
                properties =
                    mapOf(
                        "prop1" to "value1",
                        "prop2" to null,
                    ),
            )

        val mutationItem =
            EdgeBulkMutationRequest.MutationItem(
                type = EventType.INSERT,
                edge = edge,
            )

        // when
        val event = mutationItem.createEvent(schema)

        // then
        with(event.event.properties) {
            assertEquals(2, size)
            assertEquals("value1", this["prop1"])
            assertTrue(containsKey("prop2"))
            assertNull(this["prop2"])
        }
    }
}
