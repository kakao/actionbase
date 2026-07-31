package com.kakao.actionbase.engine.storage

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    /** Namespace substituted when a datastore:// URI omits it (`datastore:///<table>`). */
    val defaultNamespace: String

    fun getStorageTable(
        namespace: String,
        name: String,
    ): Mono<StorageTable>

    fun getStorageTable(uri: String): Mono<StorageTable> {
        val (ns, name) = DatastoreUri.parse(uri)
        return getStorageTable(ns.ifEmpty { defaultNamespace }, name)
    }

    companion object {
        /** Default for backends that serve one unnamed space, matching HBase's own default namespace. */
        const val DEFAULT_NAMESPACE = "default"
    }
}
