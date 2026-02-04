package com.kakao.actionbase.engine.datastore

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore

/** Memory (ByteArrayStore) compatibility test. */
class MemoryDatastoreCompatibilityTest : DatastoreCompatibilityTest() {
    private lateinit var store: ByteArrayStore

    override fun createStore(): StorageOperations {
        store = ByteArrayStore()
        return MemoryOps(store)
    }

    private class MemoryOps(
        private val s: ByteArrayStore,
    ) : StorageOperations {
        override fun get(key: ByteArray) = s[key]

        override fun getAll(keys: List<ByteArray>) = keys.mapNotNull { k -> s[k]?.let { k to it } }

        override fun scan(
            prefix: ByteArray,
            limit: Int,
        ) = s.prefixScan(prefix).take(limit).map { it.key to it.value }

        override fun put(
            key: ByteArray,
            value: ByteArray,
        ) {
            s[key] = value
        }

        override fun delete(key: ByteArray) {
            s.remove(key)
        }

        override fun increment(
            key: ByteArray,
            delta: Long,
        ) = s.increment(key, delta)

        override fun batch(mutations: List<Mutation>) =
            mutations.forEach { m ->
                when (m) {
                    is Mutation.Put -> s[m.key] = m.value
                    is Mutation.Delete -> s.remove(m.key)
                    is Mutation.Increment -> s.increment(m.key, m.delta)
                }
            }

        override fun setIfNotExists(
            key: ByteArray,
            value: ByteArray,
        ) = s.checkAndSet(key, null, value)

        override fun deleteIfEquals(
            key: ByteArray,
            expectedValue: ByteArray,
        ) = s.checkAndSet(key, expectedValue, null)
    }
}
