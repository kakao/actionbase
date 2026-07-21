package com.kakao.actionbase.server.api.queue.v1.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.queue.QueueDescriptorCodec
import com.kakao.actionbase.engine.queue.QueueMeta
import com.kakao.actionbase.engine.queue.QueueSchema
import com.kakao.actionbase.server.api.graph.v3.metadata.TableCreateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.TableUpdateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.V3CompatService

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

import reactor.core.publisher.Mono

/** Queue DDL over the v3 table API: builds the backing `ImmutableEdge` table; runtime lives in the engine. */
@Service
class QueueMetadataService(
    private val compat: V3CompatService,
) {
    fun createQueue(
        namespace: String,
        request: QueueCreateRequest,
    ): Mono<QueueDescriptorResponse> {
        val schema =
            ModelSchema.ImmutableEdge(
                source = Field(type = PrimitiveType.LONG, comment = "partition"),
                target = Field(type = PrimitiveType.STRING, comment = "message id (ULID)"),
                properties =
                    listOf(
                        StructField(QueueSchema.SEQ, PrimitiveType.LONG, "order / due time", false),
                        StructField(QueueSchema.VALUE, PrimitiveType.STRING, "opaque value (json)", true),
                    ),
                direction = DirectionType.OUT,
                indexes = listOf(Index(QueueSchema.SEQ, listOf(IndexField(QueueSchema.SEQ, Order.ASC)))),
            )
        val tableRequest =
            TableCreateRequest(
                table = request.queue,
                schema = schema,
                storage = request.storage,
                mode = MutationMode.SYNC,
                comment = QueueDescriptorCodec.encode(QueueMeta(request.partitions)),
            )
        return compat.createTable(namespace, request.queue, tableRequest).map { it.toQueueResponse() }
    }

    fun getQueue(
        namespace: String,
        queue: String,
    ): Mono<QueueDescriptorResponse> = compat.getTable(namespace, queue).map { it.toQueueResponse() }

    fun setActive(
        namespace: String,
        queue: String,
        active: Boolean,
    ): Mono<QueueDescriptorResponse> = compat.updateTable(namespace, queue, TableUpdateRequest(active = active)).map { it.toQueueResponse() }

    /** Deletion is guarded: a queue must be deactivated (see [setActive]) before it can be removed. */
    fun deleteQueue(
        namespace: String,
        queue: String,
    ): Mono<Void> =
        compat.getTable(namespace, queue).flatMap { descriptor ->
            if (descriptor.active) {
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

    private fun TableDescriptor<*>.toQueueResponse(): QueueDescriptorResponse {
        val meta =
            QueueDescriptorCodec.decode(comment)
                ?: throw IllegalArgumentException("`$database.$table` is not a queue/v1 table")
        return QueueDescriptorResponse(
            namespace = database,
            queue = table,
            partitions = meta.partitions,
            storage = storage,
        )
    }
}
