package com.kakao.actionbase.core.vertex.payload

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload

data class DataFrameVertexPayload(
    val vertices: List<VertexPayload>,
    val count: Int,
    val total: Long,
    val context: Map<String, Any?>,
) {
    companion object {
        fun from(frame: DataFrameEdgePayload): DataFrameVertexPayload =
            DataFrameVertexPayload(
                vertices = frame.edges.map { VertexPayload(version = it.version, id = it.source, properties = it.properties, context = it.context) },
                count = frame.count,
                total = frame.total,
                context = frame.context,
            )
    }
}
