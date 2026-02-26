package com.kakao.actionbase.v2.engine.storage.slatedb

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import io.slatedb.SlateDb
import io.slatedb.SlateDbConfig
import reactor.test.StepVerifier

class SlateDbTableTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var table: SlateDbTable

    @BeforeEach
    fun setUp() {
        SlateDb.initLogging(SlateDbConfig.LogLevel.INFO)

        val db =
            SlateDb.builder("data", "file://${tempDir.toAbsolutePath()}", null).use { builder ->
                builder.withMergeOperator(incrementMergeOperator)
                builder.build()
            }
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

    // -- merge operator tests (degree counting use case) --

    @Test
    fun `merge on non-existent key initializes from zero`() {
        val key = "degree:user:1".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(
                table.merge(key, 1L.toSlateBytes()).then(table.get(key)),
            ).expectNextMatches { it.toLong() == 1L }
            .verifyComplete()
    }

    @Test
    fun `sequential merges accumulate — edge insert and delete`() {
        val key = "degree:user:2".toByteArray(StandardCharsets.UTF_8)

        // 3 edges inserted
        StepVerifier
            .create(
                table
                    .merge(key, 1L.toSlateBytes())
                    .then(table.merge(key, 1L.toSlateBytes()))
                    .then(table.merge(key, 1L.toSlateBytes()))
                    .then(table.get(key)),
            ).expectNextMatches { it.toLong() == 3L }
            .verifyComplete()

        // 1 edge deleted
        StepVerifier
            .create(
                table.merge(key, (-1L).toSlateBytes()).then(table.get(key)),
            ).expectNextMatches { it.toLong() == 2L }
            .verifyComplete()
    }

    @Test
    fun `merge survives flush — degree persists across memtable rotation`() {
        val key = "degree:user:3".toByteArray(StandardCharsets.UTF_8)

        StepVerifier
            .create(
                table
                    .merge(key, 5L.toSlateBytes())
                    .then(table.flush())
                    .then(table.merge(key, 3L.toSlateBytes()))
                    .then(table.get(key)),
            ).expectNextMatches { it.toLong() == 8L }
            .verifyComplete()
    }

    @Test
    fun `batch increment adds delta to value`() {
        val key = "counter".toByteArray(StandardCharsets.UTF_8)

        // Increment non-existent key (starts at 0)
        StepVerifier
            .create(
                table
                    .batch(listOf(BatchOperation.Increment(key, 5)))
                    .then(table.get(key)),
            ).expectNextMatches { it.toLong() == 5L }
            .verifyComplete()

        // Increment existing key
        StepVerifier
            .create(
                table
                    .batch(listOf(BatchOperation.Increment(key, 3)))
                    .then(table.get(key)),
            ).expectNextMatches { it.toLong() == 8L }
            .verifyComplete()
    }
}
