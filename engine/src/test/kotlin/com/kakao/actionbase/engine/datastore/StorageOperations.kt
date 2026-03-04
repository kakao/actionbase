package com.kakao.actionbase.engine.datastore

/**
 * Storage operations interface for compatibility testing.
 *
 * Defines the minimal operations required for Actionbase storage backends.
 */
interface StorageOperations {
    fun get(key: ByteArray): ByteArray?

    fun getAll(keys: List<ByteArray>): List<Pair<ByteArray, ByteArray>>

    fun scan(
        prefix: ByteArray,
        limit: Int,
    ): List<Pair<ByteArray, ByteArray>>

    fun put(
        key: ByteArray,
        value: ByteArray,
    )

    fun delete(key: ByteArray)

    fun increment(
        key: ByteArray,
        delta: Long,
    ): Long

    fun batch(mutations: List<Mutation>)

    fun setIfNotExists(
        key: ByteArray,
        value: ByteArray,
    ): Boolean

    fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Boolean
}

sealed class Mutation {
    class Put(
        val key: ByteArray,
        val value: ByteArray,
    ) : Mutation()

    class Delete(
        val key: ByteArray,
    ) : Mutation()

    class Increment(
        val key: ByteArray,
        val delta: Long,
    ) : Mutation()
}
