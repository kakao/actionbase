package com.kakao.actionbase.server.api.queue.v1.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.engine.queue.QueueMetadata
import com.kakao.actionbase.engine.queue.QueueMetadataCodec
import com.kakao.actionbase.engine.queue.QueueSchema
import com.kakao.actionbase.server.api.graph.v3.metadata.TableCreateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.TableResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.TableSchema
import com.kakao.actionbase.server.api.graph.v3.metadata.TableType
import com.kakao.actionbase.server.api.graph.v3.metadata.TableUpdateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.V3CompatService
import com.kakao.actionbase.server.api.graph.v3.metadata.requireResult
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

import reactor.core.publisher.Mono

/** Queue DDL over the v3 table API: builds the backing `ImmutableEdge` table; runtime lives in the engine. */
@Service
class QueueMetadataService(
    private val compat: V3CompatService,
    private val graph: Graph,
) {
    fun createQueue(
        namespace: String,
        request: QueueCreateRequest,
    ): Mono<QueueDescriptorResponse> {
        val schema =
            TableSchema(
                source = TableSchema.Key(type = VertexType.LONG, comment = "partition"),
                target = TableSchema.Key(type = VertexType.STRING, comment = "message id (ULID)"),
                properties =
                    listOf(
                        TableSchema.Property(QueueSchema.SEQ, DataType.LONG, false, "order / due time"),
                        TableSchema.Property(QueueSchema.VALUE, DataType.STRING, true, "opaque value (json)"),
                    ),
            )
        val tableRequest =
            TableCreateRequest(
                table = request.queue,
                type = TableType.IMMUTABLE_EDGE,
                schema = schema,
                direction = DirectionType.OUT,
                storage = request.storage,
                indexes = listOf(Index(QueueSchema.SEQ, listOf(IndexField(QueueSchema.SEQ, Order.ASC)))),
                mode = MutationMode.SYNC,
                comment = QueueMetadataCodec.encode(QueueMetadata(request.partitions)),
            )
        return compat
            .createTable(namespace, request.queue, tableRequest)
            // Force a label-registry sync so the runtime (Graph.getLabel) sees the new queue at once,
            // rather than waiting for the next metastore reload (which may be disabled locally).
            .flatMap { status -> graph.updateLabels().thenReturn(status) }
            .map { it.requireResult().toQueueResponse() }
    }

    fun getQueue(
        namespace: String,
        queue: String,
    ): Mono<QueueDescriptorResponse> = compat.getTable(namespace, queue).map { it.toQueueResponse() }

    fun setActive(
        namespace: String,
        queue: String,
        active: Boolean,
    ): Mono<QueueDescriptorResponse> =
        compat
            .updateTable(namespace, queue, TableUpdateRequest(active = active))
            .map { it.requireResult().toQueueResponse() }

    /** Deletion is guarded: a queue must be deactivated (see [setActive]) before it can be removed. */
    fun deleteQueue(
        namespace: String,
        queue: String,
    ): Mono<Void> =
        compat.getTable(namespace, queue).flatMap { table ->
            if (table.active) {
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "queue `$namespace.$queue` must be disabled before deletion",
                    ),
                )
            } else {
                compat.deleteTable(namespace, queue).then()
            }
        }

    private fun TableResponse.toQueueResponse(): QueueDescriptorResponse {
        val meta =
            QueueMetadataCodec.decode(comment)
                ?: throw IllegalArgumentException("`$database.$table` is not a queue/v1 table")
        return QueueDescriptorResponse(
            namespace = database,
            queue = table,
            partitions = meta.numPartitions,
            storage = storage,
        )
    }
}
