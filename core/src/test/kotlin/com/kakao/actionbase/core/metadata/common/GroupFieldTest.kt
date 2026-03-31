package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.types.PrimitiveType

import kotlin.test.assertEquals
import kotlin.test.assertIs

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GroupFieldTest {
    @Nested
    inner class BackwardCompatibility {
        @Test
        fun `legacy field without type deserializes and casts correctly after type resolution`() {
            // 1. before type resolution — returns raw String (the bug)
            val field = Group.Field(name = "someLongField")
            val rawResult = field.bucketOrGet("1", ceil = false)
            assertIs<String>(rawResult)

            // 2. after type resolution (simulating resolveFieldTypes)
            val resolved = field.copy(type = PrimitiveType.LONG)
            val castedResult = resolved.bucketOrGet("1", ceil = false)
            assertIs<Long>(castedResult)
            assertEquals(1L, castedResult)
        }
    }

    @Nested
    inner class BucketOrGet {
        @Test
        fun `returns raw value when no bucket and no type`() {
            val field = Group.Field(name = "myField")
            val result = field.bucketOrGet("hello", ceil = false)
            assertEquals("hello", result)
        }

        @Test
        fun `casts String to Long when type is LONG`() {
            val field = Group.Field(name = "myField", type = PrimitiveType.LONG)
            val result = field.bucketOrGet("42", ceil = false)
            assertIs<Long>(result)
            assertEquals(42L, result)
        }

        @Test
        fun `passes value to bucket when bucket is present, ignoring type`() {
            val bucket = Bucket.Date("date_id", Bucket.ValueUnit.MILLISECOND, "+09:00", "yyyy-MM-dd")
            val field = Group.Field(name = "ts", bucket = bucket, type = PrimitiveType.LONG)
            val result = field.bucketOrGet(1700000000000L, ceil = false)
            assertIs<String>(result)
        }
    }
}
