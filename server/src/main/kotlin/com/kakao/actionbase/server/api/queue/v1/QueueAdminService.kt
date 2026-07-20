package com.kakao.actionbase.server.api.queue.v1

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
import com.kakao.actionbase.server.api.graph.v3.metadata.TableCreateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.TableUpdateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.V3CompatService

import org.springframework.stereotype.Service

import reactor.core.publisher.Mono

/**
 * Builds and inspects queues on top of the immutable edge table via [V3CompatService]. A queue is
 * an `ImmutableEdge` table with a fixed shape: LONG partition source, STRING message-id target, and
 * a LONG `orderBy` property indexed ascending for per-partition poll order.
 */
@Service
class QueueAdminService(
    private val compat: V3CompatService,
) {
    fun createQueue(
        database: String,
        request: QueueCreateRequest,
    ): Mono<QueueDescriptorResponse> {
        validate(request)
        val schema =
            ModelSchema.ImmutableEdge(
                source = Field(type = PrimitiveType.LONG, comment = "partition"),
                target = Field(type = PrimitiveType.STRING, comment = "message id"),
                properties = request.properties.map { StructField(it.name, it.type, it.comment, it.nullable) },
                direction = DirectionType.OUT,
                indexes = listOf(Index(ORDER_INDEX, listOf(IndexField(request.orderBy, Order.ASC)))),
            )
        val tableRequest =
            TableCreateRequest(
                table = request.queue,
                schema = schema,
                storage = request.storage,
                mode = MutationMode.SYNC,
                comment = QueueDescriptorCodec.encode(QueueMeta(request.partitionCount, request.orderBy)),
            )
        return compat.createTable(database, request.queue, tableRequest).map { it.toQueueResponse() }
    }

    fun getQueue(
        database: String,
        queue: String,
    ): Mono<QueueDescriptorResponse> = compat.getTable(database, queue).map { it.toQueueResponse() }

    /** Deletion is a two-step DDL: a table must be deactivated before it can be removed. */
    fun deleteQueue(
        database: String,
        queue: String,
    ): Mono<Void> =
        compat
            .updateTable(database, queue, TableUpdateRequest(active = false))
            .then(compat.deleteTable(database, queue))
            .then()

    private fun validate(request: QueueCreateRequest) {
        require(request.partitionCount > 0) { "partitionCount must be positive, got ${request.partitionCount}" }
        request.properties.forEach { field ->
            require(field.name !in RESERVED_FIELDS) { "property `${field.name}` collides with a reserved field name $RESERVED_FIELDS" }
        }
        val orderField =
            request.properties.find { it.name == request.orderBy }
                ?: throw IllegalArgumentException(
                    "orderBy `${request.orderBy}` must name one of the declared properties ${request.properties.map { it.name }}",
                )
        require(orderField.type == PrimitiveType.LONG) { "orderBy field `${request.orderBy}` must be LONG, got ${orderField.type}" }
        require(!orderField.nullable) { "orderBy field `${request.orderBy}` must be non-nullable" }
    }

    private fun TableDescriptor<*>.toQueueResponse(): QueueDescriptorResponse {
        val meta = QueueDescriptorCodec.decode(comment) ?: throw IllegalArgumentException("`$database.$table` is not a queue/v1 table")
        return QueueDescriptorResponse(
            database = database,
            queue = table,
            partitionCount = meta.partitionCount,
            orderBy = meta.orderBy,
            storage = storage,
        )
    }

    companion object {
        const val ORDER_INDEX = "order"

        // v2 reserves these system field names; a queue property must not shadow them.
        val RESERVED_FIELDS = setOf("ts", "src", "tgt")
    }
}
