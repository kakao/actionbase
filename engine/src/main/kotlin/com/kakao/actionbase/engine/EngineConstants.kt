package com.kakao.actionbase.engine

object EngineConstants {
    // A label's storage is a datastore:// URI whose namespace selects the
    // backend, so the string is self-describing — no Storage entity lookup:
    //   datastore://__sys__/metastore  -> system metastore (RDB-backed) labels
    //   datastore://<namespace>/<table> -> HBase datastore
    //   datastore:///<table>            -> HBase datastore, namespace resolved
    //                                      to the backend's configured default
    // A bare string is a legacy reference to a metastore-stored Storage entity,
    // kept until migration retires it.
    const val DATASTORE_URI_PREFIX = "datastore://"

    const val METASTORE_URI = "datastore://__sys__/metastore"

    fun isSchemeUri(storage: String): Boolean = storage.startsWith(DATASTORE_URI_PREFIX)
}
