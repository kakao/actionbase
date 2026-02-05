package com.kakao.actionbase.v2.engine.storage.hbase

import org.apache.hadoop.hbase.client.Delete as HDelete
import org.apache.hadoop.hbase.client.Get as HGet
import org.apache.hadoop.hbase.client.Increment as HIncrement
import org.apache.hadoop.hbase.client.Put as HPut
import org.apache.hadoop.hbase.client.Scan as HScan

import com.kakao.actionbase.v2.engine.storage.Delete
import com.kakao.actionbase.v2.engine.storage.Get
import com.kakao.actionbase.v2.engine.storage.Increment
import com.kakao.actionbase.v2.engine.storage.Put
import com.kakao.actionbase.v2.engine.storage.Scan
import com.kakao.actionbase.v2.engine.storage.result.GetResult

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.ResultScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import io.mockk.every
import io.mockk.mockk
import reactor.test.StepVerifier

class HBaseStorageBackendTest {
    private val mockConnection: AsyncConnection = mockk()
    private val mockAdmin: AsyncAdmin = mockk()
    private val mockTable: AsyncTable<ResultScanner> = mockk()
    private val hbaseNamespace = "testNamespace"
    private val bucketName = "testBucket"
    private lateinit var backend: HBaseStorageBackend

    private val defaultFamilyBytes = "d".toByteArray()
    private val defaultQualifierBytes = "d".toByteArray()

    @BeforeEach
    fun setUp() {
        backend = HBaseStorageBackend(mockConnection, hbaseNamespace)
        every { mockConnection.admin } returns mockAdmin
        every { mockConnection.getTable<ResultScanner>(any<TableName>()) } returns mockTable
    }

    @Test
    fun `should list buckets`() {
        val tableName1 = TableName.valueOf(hbaseNamespace, "bucket1")
        val tableName2 = TableName.valueOf(hbaseNamespace, "bucket2")

        every { mockAdmin.listTableNamesByNamespace(hbaseNamespace) } returns
            CompletableFuture.completedFuture(
                listOf(tableName1, tableName2),
            )

        StepVerifier
            .create(backend.buckets().flatMap { it.names() })
            .assertNext { names ->
                assertTrue(names.containsAll(setOf("bucket1", "bucket2")))
            }.verifyComplete()
    }

    @Test
    fun `should perform put, get, scan, increment, and delete operations`() {
        val bucket = backend.bucket(bucketName).block()!!
        val key = "test_key".toByteArray()
        val value = "test_value".toByteArray()
        val tableName = TableName.valueOf(hbaseNamespace, bucketName)

        every { mockConnection.getTable<ResultScanner>(tableName) } returns mockTable

        // Mock Put
        every { mockTable.put(any<HPut>()) } returns CompletableFuture.completedFuture(null)

        // Put
        StepVerifier
            .create(bucket.put(Put(key, value)))
            .verifyComplete()

        // Mock Get - Found
        val mockFoundResult: Result = mockk()
        every { mockFoundResult.isEmpty } returns false
        every { mockFoundResult.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES) } returns value
        every { mockTable.get(any<HGet>()) } returns CompletableFuture.completedFuture(mockFoundResult)

        // Get Found
        StepVerifier
            .create(bucket.get(Get(key)))
            .assertNext { result ->
                assertTrue(result is GetResult.Found)
                assertEquals(value.contentToString(), (result as GetResult.Found).value.contentToString())
            }.verifyComplete()

        // Mock Get - Not Found
        val mockNotFoundResult: Result = mockk()
        every { mockNotFoundResult.isEmpty } returns true
        every { mockTable.get(any<HGet>()) } returns CompletableFuture.completedFuture(mockNotFoundResult)

        // Get Not Found (after deletion or non-existent)
        StepVerifier
            .create(bucket.get(Get("non_existent_key".toByteArray())))
            .assertNext { result ->
                assertTrue(result is GetResult.NotFound)
            }.verifyComplete()

        // Mock Scan
        val mockScanResult1: Result = mockk()
        every { mockScanResult1.row } returns "prefix_key1".toByteArray()
        every { mockScanResult1.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES) } returns "prefix_value1".toByteArray()

        val mockScanResult2: Result = mockk()
        every { mockScanResult2.row } returns "prefix_key2".toByteArray()
        every { mockScanResult2.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES) } returns "prefix_value2".toByteArray()

        val mockResultScanner: ResultScanner = mockk()
        every { mockResultScanner.forEach(any()) } answers {
            val consumer = arg<java.util.function.Consumer<Result>>(0)
            consumer.accept(mockScanResult1)
            consumer.accept(mockScanResult2)
            CompletableFuture.completedFuture(null)
        }
        every { mockTable.getScanner(any<HScan>()) } returns mockResultScanner

        // Scan
        StepVerifier
            .create(bucket.scan(Scan("prefix".toByteArray(), 10)))
            .assertNext { result ->
                assertEquals("prefix_key1".toByteArray().contentToString(), result.key.contentToString())
                assertEquals("prefix_value1".toByteArray().contentToString(), result.value.contentToString())
            }.assertNext { result ->
                assertEquals("prefix_key2".toByteArray().contentToString(), result.key.contentToString())
                assertEquals("prefix_value2".toByteArray().contentToString(), result.value.contentToString())
            }.verifyComplete()

        // Mock Increment
        val initialCounter = 5L
        val incrementAmount = 10L
        val incrementedCounter = initialCounter + incrementAmount

        val mockIncrementResult: Result = mockk()
        every { mockIncrementResult.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES) } returns longToBytes(incrementedCounter)
        every { mockTable.increment(any<HIncrement>()) } returns CompletableFuture.completedFuture(mockIncrementResult)

        // Increment
        StepVerifier
            .create(bucket.increment(Increment("counter".toByteArray(), incrementAmount)))
            .assertNext { result ->
                assertEquals(incrementedCounter, result)
            }.verifyComplete()

        // Mock Delete
        every { mockTable.delete(any<HDelete>()) } returns CompletableFuture.completedFuture(null)

        // Delete
        StepVerifier
            .create(bucket.delete(Delete(key)))
            .verifyComplete()
    }

    private fun longToBytes(long: Long): ByteArray {
        val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
        buffer.putLong(long)
        return buffer.array()
    }
}
