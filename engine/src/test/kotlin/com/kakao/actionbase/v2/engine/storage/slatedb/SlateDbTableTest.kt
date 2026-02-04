package com.kakao.actionbase.v2.engine.storage.slatedb

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import io.slatedb.SlateDb
import reactor.test.StepVerifier

class SlateDbTableTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var table: SlateDbTable

    private fun findLibraryPath(): String {
        var dir = Path.of(System.getProperty("user.dir"))
        while (!dir.resolve("settings.gradle.kts").toFile().exists() && dir.parent != null) {
            dir = dir.parent
        }
        return dir.resolve("native/lib/libslatedb_c.dylib").toAbsolutePath().toString()
    }

    @BeforeEach
    fun setUp() {
        val libraryPath = findLibraryPath()
        SlateDb.loadLibrary(libraryPath)

        val fileUrl = "file://${tempDir.toAbsolutePath()}"
        val dbPath = "data"
        val db = SlateDb.open(dbPath, fileUrl, null)
        table = SlateDbTable.create(db)
    }

    @AfterEach
    fun tearDown() {
        table.close()
    }

    @Test
    fun `put and get with reactive API`() {
        val key = "hello".toByteArray(StandardCharsets.UTF_8)
        val value = "world".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(
                table.put(key, value).then(table.get(key)),
            ).expectNextMatches { it != null && String(it, StandardCharsets.UTF_8) == "world" }
            .verifyComplete()
    }

    @Test
    fun `get non-existent key returns empty`() {
        val key = "nonexistent".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(table.get(key))
            .verifyComplete()
    }

    @Test
    fun `delete removes key`() {
        val key = "to-delete".toByteArray(StandardCharsets.UTF_8)
        val value = "value".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(
                table
                    .put(key, value)
                    .then(table.delete(key))
                    .then(table.get(key)),
            ).verifyComplete()
    }

    @Test
    fun `flush persists data`() {
        val key = "flush-key".toByteArray(StandardCharsets.UTF_8)
        val value = "flush-value".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(
                table
                    .put(key, value)
                    .then(table.flush())
                    .then(table.get(key)),
            ).expectNextMatches { it != null && String(it, StandardCharsets.UTF_8) == "flush-value" }
            .verifyComplete()
    }

    @Test
    fun `batch writes multiple keys atomically`() {
        val key1 = "batch-key-1".toByteArray(StandardCharsets.UTF_8)
        val value1 = "batch-value-1".toByteArray(StandardCharsets.UTF_8)
        val key2 = "batch-key-2".toByteArray(StandardCharsets.UTF_8)
        val value2 = "batch-value-2".toByteArray(StandardCharsets.UTF_8)
        val key3 = "batch-key-3".toByteArray(StandardCharsets.UTF_8)
        val value3 = "batch-value-3".toByteArray(StandardCharsets.UTF_8)

        val operations =
            listOf(
                BatchOperation.Put(key1, value1),
                BatchOperation.Put(key2, value2),
                BatchOperation.Put(key3, value3),
            )

        StepVerifier
            .create(
                table
                    .batch(operations)
                    .then(table.get(key1)),
            ).expectNextMatches { String(it, StandardCharsets.UTF_8) == "batch-value-1" }
            .verifyComplete()

        StepVerifier
            .create(table.get(key2))
            .expectNextMatches { String(it, StandardCharsets.UTF_8) == "batch-value-2" }
            .verifyComplete()
    }

    @Test
    fun `batch with put and delete`() {
        val key1 = "batch-put".toByteArray(StandardCharsets.UTF_8)
        val value1 = "value".toByteArray(StandardCharsets.UTF_8)
        val key2 = "batch-delete".toByteArray(StandardCharsets.UTF_8)
        val value2 = "to-be-deleted".toByteArray(StandardCharsets.UTF_8)

        // First, put key2
        StepVerifier
            .create(table.put(key2, value2))
            .verifyComplete()

        // Then batch: put key1, delete key2
        val operations =
            listOf(
                BatchOperation.Put(key1, value1),
                BatchOperation.Delete(key2),
            )

        StepVerifier
            .create(
                table
                    .batch(operations)
                    .then(table.get(key1)),
            ).expectNextMatches { String(it, StandardCharsets.UTF_8) == "value" }
            .verifyComplete()

        // key2 should be deleted
        StepVerifier
            .create(table.get(key2))
            .verifyComplete()
    }
}
