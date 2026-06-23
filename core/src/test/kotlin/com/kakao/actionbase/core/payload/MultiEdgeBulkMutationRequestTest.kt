package com.kakao.actionbase.core.payload

import com.kakao.actionbase.core.edge.MultiEdge
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.types.PrimitiveType

import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class MultiEdgeBulkMutationRequestTest {
    private val schema =
        ModelSchema.MultiEdge(
            id = Field(PrimitiveType.LONG, "id"),
            source = Field(PrimitiveType.LONG, "source"),
            target = Field(PrimitiveType.LONG, "target"),
            properties =
                listOf(
                    StructField(name = "required", type = PrimitiveType.STRING, comment = "required field", nullable = false),
                    StructField(name = "optional", type = PrimitiveType.STRING, comment = "optional field", nullable = true),
                ),
            direction = DirectionType.BOTH,
            indexes = emptyList(),
            groups = emptyList(),
        )

    private fun item(
        type: EventType,
        properties: Map<String, Any?>,
    ) = MultiEdgeBulkMutationRequest.MutationItem(
        type = type,
        edge =
            MultiEdge(
                id = "1",
                source = "100",
                target = "200",
                version = 1L,
                properties = properties,
            ),
    )

    @Test
    fun `INSERT with all required fields succeeds`() {
        val event = item(EventType.INSERT, mapOf("required" to "value")).createEvent(schema)
        assertTrue(event.event.properties.containsKey("required"))
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
    fun `DELETE missing non-nullable field succeeds (tombstone)`() {
        // DELETE is a tombstone — no schema property validation; _source/_target system fields may be present
        val event = item(EventType.DELETE, emptyMap()).createEvent(schema)
        assertTrue(!event.event.properties.containsKey("required"))
        assertTrue(!event.event.properties.containsKey("optional"))
    }
}
