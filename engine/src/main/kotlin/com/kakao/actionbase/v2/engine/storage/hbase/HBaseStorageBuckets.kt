package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.AsyncUtils
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncAdmin

import reactor.core.publisher.Mono

class HBaseStorageBuckets(
    private val admin: AsyncAdmin,
    private val hbaseNamespace: String,
) : StorageBuckets {
    override fun names(): Mono<Set<String>> =
        AsyncUtils
            .asMono(admin.listTableNamesByNamespace(hbaseNamespace))
            .map { it.map(TableName::getNameAsString).toSet() }
}
