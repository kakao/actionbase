package com.kakao.actionbase.engine.queue

/**
 * The fixed shape of a queue's backing immutable edge table. `source` is the partition (LONG) and
 * `target` the message id (STRING, ULID). Two system properties: `seq` (LONG, indexed ASC for
 * per-partition poll order) and `value` (STRING, an opaque JSON blob). Shared by the admin path that
 * builds the table and the runtime path that reads/writes it.
 */
object QueueSchema {
    const val SEQ_FIELD = "seq"
    const val VALUE_FIELD = "value"
    const val SEQ_INDEX = "seq"
}
