package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageTable

/** Memory (ByteArrayStore) compatibility test for StorageTable. */
class MemoryStorageTableCompatibilityTest : StorageTableCompatibilityTest() {
    override fun createTable(): StorageTable = MemoryStorageTable(ByteArrayStore())
}
