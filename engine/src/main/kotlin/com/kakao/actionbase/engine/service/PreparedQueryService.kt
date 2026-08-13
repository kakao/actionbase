package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.engine.query.PreparedQuery
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.QueryEntity
import com.kakao.actionbase.v2.engine.service.ddl.QueryAliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.QueryAliasDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.QueryAliasUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.QueryCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.QueryDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.QueryUpdateRequest
import com.kakao.actionbase.v2.engine.sql.StatKey

import java.util.UUID

import com.fasterxml.jackson.databind.JsonNode
import com.github.benmanes.caffeine.cache.Caffeine

import reactor.core.publisher.Mono

class PreparedQueryService(
    private val graph: Graph,
    private val queries: QueryService,
) {
    private val byId =
        Caffeine
            .newBuilder()
            .also { builder -> graph.metastoreReloadInterval?.let { builder.expireAfterWrite(it) } }
            .maximumSize(MAXIMUM_CACHED_QUERIES)
            .build<String, Registered>()

    private val idOfAlias =
        Caffeine
            .newBuilder()
            .also { builder -> graph.metastoreReloadInterval?.let { builder.expireAfterWrite(it) } }
            .maximumSize(MAXIMUM_CACHED_QUERIES)
            .build<String, String>()

    fun register(
        desc: String,
        arguments: List<StructField>,
        fetch: JsonNode,
        transform: JsonNode,
        stats: Set<StatKey> = emptySet(),
    ): Mono<PreparedQueryDescriptor> {
        validate(arguments, fetch, transform, stats)

        val id = UUID.randomUUID().toString()
        return graph.queryDdl
            .create(EntityName.fromOrigin(id), QueryCreateRequest(desc, arguments, fetch, transform, stats))
            .then(Mono.defer { get(id) })
    }

    fun amend(
        id: String,
        desc: String? = null,
        arguments: List<StructField>? = null,
        fetch: JsonNode? = null,
        transform: JsonNode? = null,
        stats: Set<StatKey>? = null,
    ): Mono<PreparedQueryDescriptor> =
        readQuery(id).flatMap { existing ->
            validate(
                arguments ?: existing.arguments,
                fetch ?: existing.fetch,
                transform ?: existing.transform,
                stats ?: existing.stats,
            )

            graph.queryDdl
                .update(
                    EntityName.fromOrigin(id),
                    QueryUpdateRequest(desc = desc, arguments = arguments, fetch = fetch, transform = transform, stats = stats),
                ).then(Mono.fromRunnable<Void> { byId.invalidate(id) })
                .then(Mono.defer { get(id) })
        }

    fun get(id: String): Mono<PreparedQueryDescriptor> = registered(id).map { it.descriptor }

    fun list(status: MetadataStatus = MetadataStatus.ACTIVE): Mono<List<PreparedQueryDescriptor>> =
        graph.queryDdl
            .getAll(EntityName.origin)
            .map { page -> page.content.filter { status.matches(it.active) }.map { it.toDescriptor() } }

    fun delete(id: String): Mono<Void> =
        namesOf(id).flatMap { named ->
            require(named.isEmpty()) { "`$id` is still named by ${named.joinToString()}; drop those first." }

            val name = EntityName.fromOrigin(id)
            graph.queryDdl
                .update(name, QueryUpdateRequest(active = false))
                .then(graph.queryDdl.delete(name, QueryDeleteRequest()))
                .then(Mono.fromRunnable { byId.invalidate(id) })
        }

    fun createAlias(
        alias: String,
        desc: String,
        target: String,
    ): Mono<PreparedQueryAliasDescriptor> {
        require(alias.matches(ALIAS)) { "Invalid alias: ${Constants.Name.MESSAGE}" }
        require(alias != ALIASES_PATH) { "`$ALIASES_PATH` is reserved: it is the path the alias list is read from." }

        val name = EntityName.fromOrigin(alias)
        return readAlias(name)
            .flatMap<PreparedQueryAliasDescriptor> { Mono.error { IllegalArgumentException("alias name already exists : $alias") } }
            .switchIfEmpty(
                Mono.defer {
                    requireQuery(target)
                        .then(graph.queryAliasDdl.create(name, QueryAliasCreateRequest(desc, target)))
                        .then(Mono.defer { alias(alias) })
                },
            )
    }

    fun updateAlias(
        alias: String,
        desc: String? = null,
        target: String? = null,
        active: Boolean? = null,
    ): Mono<PreparedQueryAliasDescriptor> {
        val name = EntityName.fromOrigin(alias)
        return readAlias(name)
            .switchIfEmpty(Mono.error { NoSuchPreparedQueryException(alias) })
            .flatMap { target?.let { requireQuery(it).thenReturn(Unit) } ?: Mono.just(Unit) }
            .flatMap { graph.queryAliasDdl.update(name, QueryAliasUpdateRequest(active = active, desc = desc, target = target)) }
            .then(Mono.fromRunnable<Void> { idOfAlias.invalidate(alias) })
            .then(Mono.defer { alias(alias) })
    }

    fun alias(alias: String): Mono<PreparedQueryAliasDescriptor> =
        readAlias(EntityName.fromOrigin(alias))
            .map { PreparedQueryAliasDescriptor(it.alias, it.target, it.active, it.desc) }
            .switchIfEmpty(Mono.error { NoSuchPreparedQueryException(alias) })

    fun aliases(status: MetadataStatus = MetadataStatus.ACTIVE): Mono<List<PreparedQueryAliasDescriptor>> =
        graph.queryAliasDdl
            .getAll(EntityName.origin)
            .map { page ->
                page.content
                    .filter { status.matches(it.active) }
                    .map { PreparedQueryAliasDescriptor(it.alias, it.target, it.active, it.desc) }
            }

    fun deleteAlias(alias: String): Mono<Void> {
        val name = EntityName.fromOrigin(alias)
        return readAlias(name)
            .switchIfEmpty(Mono.error { NoSuchPreparedQueryException(alias) })
            .flatMap {
                graph.queryAliasDdl
                    .update(name, QueryAliasUpdateRequest(active = false))
                    .then(graph.queryAliasDdl.delete(name, QueryAliasDeleteRequest()))
            }.then(Mono.fromRunnable { idOfAlias.invalidate(alias) })
    }

    fun query(
        id: String,
        arguments: Map<String, Any>,
    ): Mono<Map<String, DataFrame>> = registered(id).flatMap { queries.query(it.prepared, arguments) }

    private fun registered(id: String): Mono<Registered> =
        if (id.matches(ALIAS)) {
            Mono
                .justOrEmpty(idOfAlias.getIfPresent(id))
                .switchIfEmpty(
                    readAlias(EntityName.fromOrigin(id))
                        .map { it.target }
                        .doOnNext { idOfAlias.put(id, it) },
                ).switchIfEmpty(Mono.error { NoSuchPreparedQueryException(id) })
                .flatMap { target -> loaded(target) }
        } else {
            loaded(id)
        }

    private fun loaded(id: String): Mono<Registered> =
        Mono
            .justOrEmpty(byId.getIfPresent(id))
            .switchIfEmpty(
                Mono.defer {
                    readQuery(id)
                        .map { entity -> Registered(entity.toDescriptor(), entity.toPrepared()) }
                        .doOnNext { byId.put(id, it) }
                },
            ).switchIfEmpty(Mono.error { NoSuchPreparedQueryException(id) })

    private fun readQuery(id: String): Mono<QueryEntity> = graph.queryDdl.getSingle(EntityName.fromOrigin(id)).filter { it.active }

    private fun requireQuery(id: String): Mono<QueryEntity> = readQuery(id).switchIfEmpty(Mono.error { NoSuchPreparedQueryException(id) })

    private fun readAlias(name: EntityName) = graph.queryAliasDdl.getSingle(name).filter { it.active }

    private fun namesOf(id: String): Mono<List<String>> = aliases(MetadataStatus.ALL).map { all -> all.filter { it.target == id }.map { it.alias } }

    private fun validate(
        arguments: List<StructField>,
        fetch: JsonNode,
        transform: JsonNode,
        stats: Set<StatKey>,
    ) {
        val prepared = PreparedQuery.of(fetch, transform, stats, arguments)
        prepared.bind(arguments.associate { it.name to sample(it) })
    }

    private fun sample(argument: StructField): Any = argument.type.cast(SAMPLE_TEXT)

    private fun QueryEntity.toPrepared(): PreparedQuery = PreparedQuery.of(fetch, transform, stats, arguments)

    private fun QueryEntity.toDescriptor(): PreparedQueryDescriptor =
        PreparedQueryDescriptor(
            id = id,
            arguments = arguments,
            fetch = fetch,
            transform = transform,
            stats = stats,
            active = active,
            desc = desc,
        )

    private data class Registered(
        val descriptor: PreparedQueryDescriptor,
        val prepared: PreparedQuery,
    )

    private companion object {
        val ALIAS = Regex(Constants.Name.PATTERN)
        const val ALIASES_PATH = "aliases"
        const val MAXIMUM_CACHED_QUERIES = 1_000L

        const val SAMPLE_TEXT = "1"
    }
}

class NoSuchPreparedQueryException(
    id: String,
) : NoSuchElementException("No prepared query `$id`.")

enum class MetadataStatus {
    ACTIVE,
    INACTIVE,
    ALL,
    ;

    fun matches(active: Boolean): Boolean =
        when (this) {
            ACTIVE -> active
            INACTIVE -> !active
            ALL -> true
        }
}

data class PreparedQueryDescriptor(
    val id: String,
    val arguments: List<StructField>,
    val fetch: JsonNode,
    val transform: JsonNode,
    val stats: Set<StatKey>,
    val active: Boolean,
    val desc: String,
)

data class PreparedQueryAliasDescriptor(
    val alias: String,
    val target: String,
    val active: Boolean,
    val desc: String,
)
