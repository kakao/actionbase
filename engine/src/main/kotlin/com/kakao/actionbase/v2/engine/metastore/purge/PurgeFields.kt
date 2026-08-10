package com.kakao.actionbase.v2.engine.metastore.purge

import com.kakao.actionbase.v2.core.code.hbase.OrderedBytes
import com.kakao.actionbase.v2.core.code.hbase.SimplePositionedMutableByteRange
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils

import java.util.Base64

/**
 * Reads the two fields the purge needs out of a stored row, and nothing else.
 *
 * The metastore's own decoders build an edge: they walk every property, resolve names through a
 * schema, and hand back a model object. None of that is wanted here. The purge asks whether a row
 * is inactive and which service it belongs to, so it reads the front of each encoding and stops.
 * That keeps the tool off the data model entirely - only the byte-level primitives below it.
 *
 * How old a tombstone is comes from the `update_ts` column instead of the encoded `delete_ts`,
 * because reaching that property means walking the whole property list. For a row that is currently
 * inactive the last write was the one that deactivated it, so the column says the same thing, and
 * it says it in a `WHERE` clause against an index rather than after the row was read.
 */
internal object PurgeFields {
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** `1` is the only active code; `0` and the retired `2` both mean inactive. */
    private const val ACTIVE_CODE: Byte = 1

    /**
     * The value opens with the active flag, so decoding stops after one field. Everything past it
     * is the timestamp and the property list, which the purge has no use for.
     */
    fun isActive(v: String): Boolean = OrderedBytes.decodeInt8(SimplePositionedMutableByteRange(decoder.decode(v))) == ACTIVE_CODE

    /**
     * The key opens with a hash of the source, then the source itself. A metastore row's source is
     * the service it belongs to, which is what scopes a purge.
     *
     * The field suffix after `:` is not read - the source sits before it.
     */
    fun serviceOf(k: String): String {
        val range = SimplePositionedMutableByteRange(decoder.decode(k.substringBefore(':')))
        range.getInt() // the source hash, which the source itself follows
        return ValueUtils.deserialize<Any>(range).toString()
    }
}
