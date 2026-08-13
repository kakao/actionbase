package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.sql.RowWithSchema

data class QueryAliasEntity(
    override val active: Boolean,
    override val name: EntityName,
    val desc: String,
    val target: String,
) : EdgeEntity {
    val alias: String
        get() = name.nameNotNull

    override fun toEdge(): TraceEdge =
        name.toTraceEdge(
            props =
                mapOf(
                    "props_active" to active,
                    "desc" to desc,
                    "target" to target,
                ),
        )

    companion object : EntityFactory<QueryAliasEntity> {
        override fun toEntity(edge: HashEdge): QueryAliasEntity =
            QueryAliasEntity(
                active = (edge.props.getOrDefault("props_active", null) ?: true).toString().toBoolean(),
                name = EntityName.withPhase(edge.src.toString(), edge.tgt.toString()),
                desc = edge.props["desc"].toString(),
                target = edge.props["target"].toString(),
            )

        override fun toEntity(row: RowWithSchema): QueryAliasEntity =
            QueryAliasEntity(
                active = (row.getOrNull("props_active") ?: true).toString().toBoolean(),
                name = EntityName.withPhase(row.getString("src"), row.getString("tgt")),
                desc = row.getString("desc"),
                target = row.getString("target"),
            )
    }
}
