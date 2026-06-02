package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.entity.EntityName

sealed interface MutateEdgeRequest {
    val label: String
    val edges: List<Edge>
    val audit: Audit
    val requestId: String

    val name: EntityName
        get() = EntityName.of(label)
}

data class InsertEdgeRequest(
    override val label: String,
    override val edges: List<Edge>,
    override val audit: Audit = Audit.default,
    override val requestId: String = "",
) : MutateEdgeRequest

data class DeleteEdgeRequest(
    override val label: String,
    override val edges: List<Edge>,
    override val audit: Audit = Audit.default,
    override val requestId: String = "",
) : MutateEdgeRequest
