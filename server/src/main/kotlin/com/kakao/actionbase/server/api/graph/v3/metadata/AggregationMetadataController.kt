package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class AggregationMetadataController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping("/graph/v3/metadata/aggregations/topk")
    fun list(): Mono<ResponseEntity<TopkMetadataResponse>> =
        v3CompatService
            .getDatabases()
            .flatMapMany { databases -> Flux.fromIterable(databases) }
            .flatMap { db -> v3CompatService.getTables(db.database) }
            .flatMap { tables -> Flux.fromIterable(tables) }
            .flatMap { table -> Flux.fromIterable(toEntries(table)) }
            .collectList()
            .map { entries -> ResponseEntity.ok(TopkMetadataResponse(groups = entries)) }

    private fun toEntries(table: TableDescriptor<*>): List<TopkMetadata> {
        val (schemaType, groups) =
            when (val schema = table.schema) {
                is ModelSchema.Edge -> "EDGE" to schema.groups
                is ModelSchema.MultiEdge -> "MULTI_EDGE" to schema.groups
                is ModelSchema.Vertex -> return emptyList()
            }
        return groups
            .filter { !it.aggregations?.topk.isNullOrEmpty() }
            .map { group ->
                TopkMetadata(
                    label = "${table.database}.${table.table}",
                    database = table.database,
                    table = table.table,
                    schemaType = schemaType,
                    group = group.group,
                    directionType = group.directionType,
                    fields = group.fields,
                    aggregations = group.aggregations!!,
                )
            }
    }
}

data class TopkMetadataResponse(
    val groups: List<TopkMetadata>,
)

data class TopkMetadata(
    val label: String,
    val database: String,
    val table: String,
    val schemaType: String,
    val group: String,
    val directionType: DirectionType,
    val fields: List<Group.Field>,
    val aggregations: Aggregations,
)
