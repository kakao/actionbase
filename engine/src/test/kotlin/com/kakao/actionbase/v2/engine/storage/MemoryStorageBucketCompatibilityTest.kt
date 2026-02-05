package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBucket

/** Memory (ByteArrayStore) compatibility test for StorageBucket. */
class MemoryStorageBucketCompatibilityTest : StorageBucketCompatibilityTest() {
    override fun createBucket(): StorageBucket = MemoryStorageBucket(ByteArrayStore())
}
