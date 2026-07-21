package com.kakao.actionbase.engine.queue

/** Fixed field names of a queue's backing table: `seq` (LONG order, also the index name) and opaque `value`. */
object QueueSchema {
    const val SEQ = "seq"
    const val VALUE = "value"
}
