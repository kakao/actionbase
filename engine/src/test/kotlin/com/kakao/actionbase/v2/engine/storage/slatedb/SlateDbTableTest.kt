package com.kakao.actionbase.v2.engine.storage.slatedb

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import reactor.test.StepVerifier

class SlateDbTableTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var table: SlateDbTable

    private fun findLibraryPath(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        while (!dir.resolve("settings.gradle.kts").toFile().exists() && dir.parent != null) {
            dir = dir.parent
        }
        return dir.resolve("native/lib/libslatedb_c.dylib")
    }

    @BeforeEach
    fun setUp() {
        val fileUrl = "file://${tempDir.toAbsolutePath()}"
        val dbPath = "data"
        val native = SlateDbNative.open(dbPath, fileUrl, findLibraryPath())
        table = SlateDbTable.create(native)
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
}
