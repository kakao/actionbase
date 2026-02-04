package com.kakao.actionbase.engine.datastore

import com.kakao.actionbase.v2.engine.storage.slatedb.BatchOperation
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbTable

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

import io.slatedb.SlateDb

/**
 * SlateDB compatibility test.
 *
 * Disabled by default. Set SLATEDB_TEST=true to run.
 * Requires native library: native/lib/libslatedb_c.dylib (macOS) or libslatedb_c.so (Linux)
 *
 * To run:
 *   SLATEDB_TEST=true ./gradlew :engine:test --tests "*SlateDBDatastoreCompatibilityTest*"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlateDBDatastoreCompatibilityTest : DatastoreCompatibilityTest() {
    private var table: SlateDbTable? = null
    private lateinit var tempDir: Path

    private val enabled = System.getenv("SLATEDB_TEST") == "true"

    @BeforeAll
    fun setUpSlateDB(
        @TempDir dir: Path,
    ) {
        assumeTrue(enabled, "SLATEDB_TEST=true not set")
        tempDir = dir
        SlateDb.loadLibrary(findLibraryPath())
        val db = SlateDb.open("data", "file://${tempDir.toAbsolutePath()}", null)
        table = SlateDbTable.create(db)
    }

    @AfterAll
    fun tearDownSlateDB() {
        table?.close()
    }

    override fun createStore(): StorageOperations = SlateDBOps(table!!)

    override fun supportsCheckAndMutate() = false

    override fun cleanup() {
        table?.let { t ->
            t.scanPrefix(ByteArray(0), Int.MAX_VALUE).block()?.forEach { (key, _) ->
                t.delete(key).block()
            }
        }
    }

    private fun findLibraryPath(): String {
        var dir = Path.of(System.getProperty("user.dir"))
        while (!dir.resolve("settings.gradle.kts").toFile().exists() && dir.parent != null) {
            dir = dir.parent
        }
        val libName =
            if (System.getProperty("os.name").lowercase().contains("linux")) {
                "libslatedb_c.so"
            } else {
                "libslatedb_c.dylib"
            }
        return dir.resolve("native/lib/$libName").toAbsolutePath().toString()
    }

    private class SlateDBOps(
        private val t: SlateDbTable,
    ) : StorageOperations {
        override fun get(key: ByteArray): ByteArray? = t.get(key).block()

        override fun getAll(keys: List<ByteArray>) = keys.mapNotNull { k -> t.get(k).block()?.let { k to it } }

        override fun scan(
            prefix: ByteArray,
            limit: Int,
        ) = t.scanPrefix(prefix, limit).block() ?: emptyList()

        override fun put(
            key: ByteArray,
            value: ByteArray,
        ) {
            t.put(key, value).block()
        }

        override fun delete(key: ByteArray) {
            t.delete(key).block()
        }

        override fun increment(
            key: ByteArray,
            delta: Long,
        ): Long {
            val current =
                t.get(key).block()?.let {
                    ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).long
                } ?: 0L
            val newValue = current + delta
            val bytes =
                ByteBuffer
                    .allocate(8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(newValue)
                    .array()
            t.put(key, bytes).block()
            return newValue
        }

        override fun batch(mutations: List<Mutation>) {
            val ops =
                mutations.map { m ->
                    when (m) {
                        is Mutation.Put -> BatchOperation.Put(m.key, m.value)
                        is Mutation.Delete -> BatchOperation.Delete(m.key)
                        is Mutation.Increment -> BatchOperation.Increment(m.key, m.delta)
                    }
                }
            t.batch(ops).block()
        }

        override fun setIfNotExists(
            key: ByteArray,
            value: ByteArray,
        ): Boolean = throw UnsupportedOperationException("SlateDB does not support checkAndMutate")

        override fun deleteIfEquals(
            key: ByteArray,
            expectedValue: ByteArray,
        ): Boolean = throw UnsupportedOperationException("SlateDB does not support checkAndMutate")
    }
}
