package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus

/**
 * The v3 metadata API reuses the v2 response envelopes verbatim: a listing is `{count, content}`
 * and a mutation is `{status, result, message}`. These map the v2 entity inside an envelope to its
 * v3-named response without reshaping the envelope itself.
 */
fun <E, R> DdlStatus<E>.mapResult(transform: (E) -> R): DdlStatus<R> = DdlStatus(status, result?.let(transform), message)

fun <E, R> List<E>.toPage(transform: (E) -> R): DdlPage<R> = DdlPage(count = size.toLong(), content = map(transform))

/**
 * A successful DDL mutation always carries its entity; callers that build a different response from
 * it need the entity, not the envelope.
 */
fun <R> DdlStatus<R>.requireResult(): R = requireNotNull(result) { "DDL returned status=$status without a result: $message" }
