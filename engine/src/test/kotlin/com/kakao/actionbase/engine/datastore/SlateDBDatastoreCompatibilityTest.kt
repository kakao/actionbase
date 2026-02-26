package com.kakao.actionbase.engine.datastore

import com.kakao.actionbase.v2.engine.storage.slatedb.BatchOperation
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbTable
import com.kakao.actionbase.v2.engine.storage.slatedb.incrementMergeOperator
import com.kakao.actionbase.v2.engine.storage.slatedb.toLong
import com.kakao.actionbase.v2.engine.storage.slatedb.toSlateBytes

import java.nio.file.Path

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

import io.slatedb.SlateDb
import io.slatedb.SlateDbConfig

/**
 * SlateDB compatibility test.
 *
 * Disabled by default. Set SLATEDB_TEST=true to run.
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
        SlateDb.initLogging(SlateDbConfig.LogLevel.INFO)
        val db =
            SlateDb.builder("data", "file://${tempDir.toAbsolutePath()}", null).use { builder ->
                builder.withMergeOperator(incrementMergeOperator)
                builder.build()
            }
        table = SlateDbTable.create(db)
    }

    @AfterAll
    fun tearDownSlateDB() {
        table?.close()
    }

    override fun createStore(): StorageOperations = SlateDBOperations(table!!)

    override fun supportsCheckAndMutate() = false

    override fun cleanup() {
        table?.let { t ->
            t.scanPrefix(ByteArray(0), Int.MAX_VALUE).block()?.forEach { (key, _) ->
                t.delete(key).block()
            }
        }
    }

    private class SlateDBOperations(
        private val table: SlateDbTable,
    ) : StorageOperations {
        override fun get(key: ByteArray): ByteArray? = table.get(key).block()

        override fun getAll(keys: List<ByteArray>) = keys.mapNotNull { k -> table.get(k).block()?.let { k to it } }

        override fun scan(
            prefix: ByteArray,
            limit: Int,
        ) = table.scanPrefix(prefix, limit).block() ?: emptyList()

        override fun put(
            key: ByteArray,
            value: ByteArray,
        ) {
            table.put(key, value).block()
        }

        override fun delete(key: ByteArray) {
            table.delete(key).block()
        }

        override fun increment(
            key: ByteArray,
            delta: Long,
        ): Long {
            table.merge(key, delta.toSlateBytes()).block()
            return table.get(key).block()?.toLong() ?: 0L
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
            table.batch(ops).block()
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
