package com.kakao.actionbase.v2.engine.storage.slatedb

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlateDbNativeTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var db: SlateDbNative

    companion object {
        private val LIBRARY_PATH: Path = Path.of("native/lib/libslatedb_c.dylib")
    }

    @BeforeEach
    fun setUp() {
        val dbPath = tempDir.resolve("testdb").toString()
        db = SlateDbNative.open(dbPath, LIBRARY_PATH)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `put and get`() {
        // Given
        val key = "hello".toByteArray()
        val value = "world".toByteArray()

        // When
        db.put(key, value)
        val result = db.get(key)

        // Then
        assertEquals("world", result?.decodeToString())
    }

    @Test
    fun `get non-existent key returns null`() {
        // When
        val result = db.get("nonexistent".toByteArray())

        // Then
        assertNull(result)
    }

    @Test
    fun `delete removes key`() {
        // Given
        val key = "to-delete".toByteArray()
        db.put(key, "value".toByteArray())

        // When
        db.delete(key)
        val result = db.get(key)

        // Then
        assertNull(result)
    }

    @Test
    fun `overwrite existing key`() {
        // Given
        val key = "key".toByteArray()
        db.put(key, "value1".toByteArray())

        // When
        db.put(key, "value2".toByteArray())
        val result = db.get(key)

        // Then
        assertEquals("value2", result?.decodeToString())
    }

    @Test
    fun `binary data`() {
        // Given
        val key = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
        val value = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        // When
        db.put(key, value)
        val result = db.get(key)

        // Then
        assertEquals(value.toList(), result?.toList())
    }
}
