package com.kakao.actionbase.v2.engine.test.dsl

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

@GraphDsl
class DmlScope(
    val label: Label,
) {
    @GraphDsl
    fun insert(
        dsl: EdgeDsl,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.INSERT)
    }

    @GraphDsl
    fun insert(
        src: Any,
        tgt: Any,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        val dsl = EdgeDsl(src, tgt)
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.INSERT)
    }

    @GraphDsl
    fun delete(
        dsl: EdgeDsl,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.DELETE)
    }

    @GraphDsl
    fun delete(
        src: Any,
        tgt: Any,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        val dsl = EdgeDsl(src, tgt)
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.DELETE)
    }

    @GraphDsl
    fun update(
        dsl: EdgeDsl,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.UPDATE)
    }

    @GraphDsl
    fun update(
        src: Any,
        tgt: Any,
        block: EdgeDsl.() -> Unit = {},
    ): Mono<CdcContext> {
        val dsl = EdgeDsl(src, tgt)
        dsl.apply(block)
        return label.mutate(dsl.build(), EdgeOperation.UPDATE)
    }

    @GraphDsl
    fun scan(
        src: Any,
        dir: Direction,
        block2: Mono<DataFrame>.() -> Unit,
    ) {
        val scanFilter = ScanFilter(EntityName.origin, setOf(src), dir = dir)
        val df: Mono<DataFrame> = label.scan(scanFilter, emptySet()).cache()
        df.block2()
    }
}
