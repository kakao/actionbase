package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeLockRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory
import com.kakao.actionbase.v2.core.code.EmptyEdgeIdEncoder
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.cdc.Cdc
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.cdc.CdcFactory
import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory
import com.kakao.actionbase.v2.engine.client.web.WebClientFactory
import com.kakao.actionbase.v2.engine.compat.DefaultHBaseCluster
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.edge.MutationResultItem
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EdgeEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.QueryEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.exception.MutationError
import com.kakao.actionbase.v2.engine.fake.fakeEdges
import com.kakao.actionbase.v2.engine.label.DeleteEdgeRequest
import com.kakao.actionbase.v2.engine.label.DeleteIdEdgeRequest
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.InsertEdgeRequest
import com.kakao.actionbase.v2.engine.label.InsertIdEdgeRequest
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.label.LockAcquisitionFailedException
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.metadata.sync.MetadataSyncEntity
import com.kakao.actionbase.v2.engine.metadata.sync.MetadataSyncStatus
import com.kakao.actionbase.v2.engine.metadata.sync.MetadataType
import com.kakao.actionbase.v2.engine.metastore.MetastoreInspector
import com.kakao.actionbase.v2.engine.migration.Migration
import com.kakao.actionbase.v2.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.v2.engine.query.LabelProvider
import com.kakao.actionbase.v2.engine.service.ddl.AliasDdlService
import com.kakao.actionbase.v2.engine.service.ddl.LabelDdlService
import com.kakao.actionbase.v2.engine.service.ddl.QueryDdlService
import com.kakao.actionbase.v2.engine.service.ddl.ServiceDdlService
import com.kakao.actionbase.v2.engine.service.ddl.StorageDdlService
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.Stat
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.StatLong
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseConnections
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseOptions
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable
import com.kakao.actionbase.v2.engine.util.getLogger
import com.kakao.actionbase.v2.engine.wal.Wal
import com.kakao.actionbase.v2.engine.wal.WalFactory
import com.kakao.actionbase.v2.engine.wal.WalLog

import java.lang.AutoCloseable
import java.time.Duration
import java.util.UUID

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Suppress("LargeClass")
class Graph(
    val wal: Wal,
    val cdc: Cdc,
    override val localMetastore: Database,
    override val metastore: Database,
    override val metadataTable: MetadataTable,
    override val edgeEncoderFactory: EdgeEncoderFactory,
    override val edgeRecordMapper: EdgeRecordMapper,
    override val datastore: DefaultHBaseCluster,
    private val systemStorages: Map<EntityName, StorageEntity>,
    config: GraphConfig,
    serviceLabel: Label,
    storageLabel: Label,
    labelLabel: Label,
    infoLabel: Label,
    queryLabel: Label,
    aliasLabel: Label,
    onlineMetadataLabel: Label,
    private val nilLabel: Label,
) : GraphDefaults,
    LabelProvider,
    AutoCloseable {
    internal val mutationRequestTimeout = config.mutationRequestTimeout

    private var metadataInitialized = false

    private val phase: String = config.phase

    private val tenant: String = config.tenant

    private val artifactInfo: String = config.artifactInfo ?: "no artifact info"

    override val lockTimeout: Long = config.lockTimeout

    private val warmUpConfig = config.warmUp

    private val hostName: String = config.hostName

    private val systemLabels: Map<EntityName, Label> =
        mapOf(
            serviceLabel.name to serviceLabel,
            storageLabel.name to storageLabel,
            labelLabel.name to labelLabel,
            infoLabel.name to infoLabel,
            queryLabel.name to queryLabel,
            aliasLabel.name to aliasLabel,
            onlineMetadataLabel.name to onlineMetadataLabel,
            nilLabel.name to nilLabel,
        )

    private val predefinedLabels: MutableMap<EntityName, Label> = mutableMapOf()

    override var storages: Map<EntityName, StorageEntity> = systemStorages

    private var services: Map<EntityName, ServiceEntity> = emptyMap()
    private var labels: Map<EntityName, Label> = systemLabels + predefinedLabels
    private var aliases: Map<EntityName, EntityName> = emptyMap()
    private var aliasEntities: Map<EntityName, AliasEntity> = emptyMap()
    private var queries: Map<EntityName, QueryEntity> = emptyMap()
    private var intervalDisposable: Disposable? = null

    val serviceDdl = ServiceDdlService(this, serviceLabel, ServiceEntity)

    val storageDdl = StorageDdlService(this, storageLabel, StorageEntity)

    val labelDdl = LabelDdlService(this, labelLabel, LabelEntity)

    val queryDdl = QueryDdlService(this, queryLabel, QueryEntity)

    val aliasDdl = AliasDdlService(this, aliasLabel, AliasEntity)

    val idEdgeEncoder: IdEdgeEncoder = edgeEncoderFactory.bytesKeyValueEncoder

    fun isReady(): Boolean = metadataInitialized

    private val queryExecutor = ActionbaseQueryExecutor(this)

    val metastoreInspector = MetastoreInspector(this.metastore, this.metadataTable)

    val encoderPoolSize = config.encoderPoolSize

    init {
        if (config.metastoreReloadInitialDelay != null && config.metastoreReloadInterval != null) {
            startMetastoreReload(config.metastoreReloadInitialDelay, config.metastoreReloadInterval, log)
        } else {
            log.info("metastore reload disabled")
        }

        Mono
            .defer {
                if (metadataInitialized) {
                    Mono.fromCallable {
                        printLog(config)

                        log.info("graph initialized with config: {}", config)
                        log.info("DEFAULT_BOUNDED_ELASTIC_SIZE: {}", Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE)
                        log.info("DEFAULT_BOUNDED_ELASTIC_QUEUESIZE: {}", Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE)

                        log.info("Initialization successful!")
                        "Initialization successful!"
                    }
                } else {
                    log.info("waiting for metadata initialization...")
                    Mono.empty()
                }
            }.repeatWhenEmpty { it.delayElements(Duration.ofMillis(1000)) }
            .timeout(Duration.ofSeconds(600))
            .onErrorReturn("Initialization failed due to timeout.")
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()
    }

    private fun printLog(config: GraphConfig) {
        log.info(
            """

              ____                 _
             / ___|_ __ __ _ _ __ | |__
            | |  _| '__/ _` | '_ \| '_ \
            | |_| | | | (_| | |_) | | | |
             \____|_|  \__,_| .__/|_| |_| tenant: {}, phase: {}
                            |_|

            """.trimIndent(),
            config.tenant,
            config.phase,
        )
        log.info("graph initialized with config: {}", config)
        log.info("DEFAULT_BOUNDED_ELASTIC_SIZE: {}", Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE)
        log.info("DEFAULT_BOUNDED_ELASTIC_QUEUESIZE: {}", Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE)
    }

    // -- mutation

    override fun getLabel(name: EntityName): Label =
        if (aliases.containsKey(name)) {
            labels[aliases[name]] ?: throw UnsupportedOperationException("No such label ${aliases[name]} of the alias $name.")
        } else {
            labels[name] ?: throw UnsupportedOperationException("No such label $name.")
        }

    fun checkLabelExists(name: EntityName): Mono<Boolean> =
        labelDdl
            .getSingle(name)
            .map { true }
            .defaultIfEmpty(false)

    fun checkAliasExists(name: EntityName): Mono<Boolean> =
        aliasDdl
            .getSingle(name)
            .map { true }
            .defaultIfEmpty(false)

    fun checkStorageExists(name: EntityName): Mono<Boolean> =
        storageDdl
            .getSingle(name)
            .filter(StorageEntity::active)
            .map { true }
            .defaultIfEmpty(false)

    fun checkServiceExists(entityName: EntityName): Mono<Boolean> =
        if (entityName.service == EntityName.origin.service) {
            Mono.just(true)
        } else {
            serviceDdl
                .getSingle(EntityName.fromOrigin(entityName.service))
                .filter(ServiceEntity::active)
                .map { true }
                .defaultIfEmpty(false)
        }

    fun getAllLabels(): Map<String, String> =
        (
            labels.map {
                it.key.toString() to it.value.toString()
            } + aliases.map { it.key.toString() to "Alias(${it.value})" }
        ).toMap()

    @Suppress("LongMethod")
    fun mutate(
        alias: EntityName,
        label: Label,
        edges: List<TraceEdge>,
        operation: EdgeOperation,
        audit: Audit = Audit.default,
        requestId: String = "",
        bulk: Boolean = false,
        mode: MutationMode? = null,
        failOnExist: Boolean = false,
    ): Mono<MutationResult> {
        val mutationModeContext = MutationModeContext.of(label.entity.mode, mode)

        return Flux
            .fromIterable(edges)
            .flatMapSequential { edge ->
                // Process edges in parallel with ordered results:
                // For each edge, perform WAL write, then perform the operation (upsert or delete) sequentially.
                wal
                    .write(alias, label.name, edge, operation, audit, requestId, mutationModeContext)
                    .then(
                        Mono.defer {
                            if (mutationModeContext.queue) {
                                Mono.just(
                                    MutationResultItem(
                                        status = EdgeOperationStatus.QUEUED,
                                        traceId = edge.traceId,
                                        edge = null,
                                    ),
                                )
                            } else {
                                label
                                    .mutate(edge, operation, alias, bulk, failOnExist)
                                    .map { context ->
                                        // fill audit and requestId
                                        context.copy(audit = audit, requestId = requestId)
                                    }.doOnNext { context ->
                                        cdc
                                            .write(context)
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe()
                                    }.doOnError {
                                        val errorCdcContext =
                                            CdcContext(
                                                label.name,
                                                edge,
                                                operation,
                                                EdgeOperationStatus.ERROR,
                                                null,
                                                null,
                                                0,
                                                alias,
                                                message = it.message,
                                                requestId = requestId,
                                            )
                                        cdc
                                            .write(errorCdcContext)
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe()
                                    }.map { context ->
                                        MutationResultItem(
                                            status = context.status,
                                            traceId = context.edge.traceId,
                                            edge = context.after ?: context.before,
                                        )
                                    }
                            }
                        },
                    ).onErrorResume {
                        log.error(
                            "mutation error, edge : (requestId={}, traceId={}, ts={}, src={}, tgt={}, size(props)={})",
                            requestId,
                            edge.traceId,
                            edge.ts,
                            edge.src,
                            edge.tgt,
                            edge.props.size,
                            MutationError(it),
                        )
                        if (it is LockAcquisitionFailedException) {
                            label
                                .findStaleLockAndClear(it.lockEdge, lockTimeout)
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe()
                        }
                        Mono.just(
                            MutationResultItem(
                                status = EdgeOperationStatus.ERROR,
                                traceId = edge.traceId,
                                edge = null,
                            ),
                        )
                    }
            }.collectList()
            .map { MutationResult(it) }
            .timeout(Duration.ofMillis(mutationRequestTimeout))
            // Ensures all work completes even if the request is cancelled - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#cache--
            .cache(Duration.ZERO)
            .subscribeOn(Schedulers.boundedElastic())
    }

    fun upsert(
        request: InsertEdgeRequest,
        bulk: Boolean = false,
        mode: MutationMode? = null,
    ): Mono<MutationResult> =
        mutate(
            request.name,
            getLabel(request.name),
            request.edges.map { it.toTraceEdge() },
            EdgeOperation.INSERT,
            request.audit,
            request.requestId,
            bulk,
            mode,
        )

    fun update(
        request: InsertEdgeRequest,
        bulk: Boolean = false,
        mode: MutationMode? = null,
    ): Mono<MutationResult> =
        mutate(
            request.name,
            getLabel(request.name),
            request.edges.map { it.toTraceEdge() },
            EdgeOperation.UPDATE,
            request.audit,
            request.requestId,
            bulk,
            mode,
        )

    fun delete(
        request: DeleteEdgeRequest,
        bulk: Boolean = false,
        mode: MutationMode? = null,
    ): Mono<MutationResult> =
        mutate(
            request.name,
            getLabel(request.name),
            request.edges.map { it.toTraceEdge() },
            EdgeOperation.DELETE,
            request.audit,
            request.requestId,
            bulk,
            mode,
        )

    fun purge(request: DeleteEdgeRequest): Mono<MutationResult> =
        mutate(
            request.name,
            getLabel(request.name),
            request.edges.map {
                it.toTraceEdge()
            },
            EdgeOperation.PURGE,
            request.audit,
            request.requestId,
            false,
        )

    fun upsert(request: InsertIdEdgeRequest): Mono<MutationResult> = upsert(request.toInsertEdgeRequest(idEdgeEncoder))

    fun update(request: InsertIdEdgeRequest): Mono<MutationResult> = update(request.toInsertEdgeRequest(idEdgeEncoder))

    fun delete(request: DeleteIdEdgeRequest): Mono<MutationResult> = delete(request.toDeleteEdgeRequest(idEdgeEncoder))

    // -- query

    fun singleStepQuery(
        scanFilter: ScanFilter,
        stats: Set<StatKey> = emptySet(),
    ): Mono<DataFrame> {
        val label = getLabel(scanFilter.name)
        log.debug("scanFilter: {}", scanFilter)
        log.debug("label: {}", label)

        val dfMono =
            if (scanFilter.selfEdge) {
                label.getSelf(scanFilter.srcSet.toList(), stats, idEdgeEncoder)
            } else if (scanFilter.srcSet.size == 1 && scanFilter.tgt != null) {
                label.get(scanFilter.srcSet.first(), scanFilter.tgt.toList(), scanFilter.dir, stats, idEdgeEncoder)
            } else if ("COUNT(1)" in scanFilter.selectFields && scanFilter.selectFields.size == 1 && scanFilter.tgt == null) {
                label.count(scanFilter.srcSet, scanFilter.dir)
            } else {
                require(scanFilter.tgt == null) { "tgt should be null" }
                label.scan(scanFilter, stats, idEdgeEncoder)
            }

        val statMonos: List<Mono<Stat<*>>> =
            stats.mapNotNull { statName ->
                when (statName) {
                    // used in HashLabel or IndexedLabel
                    StatKey.SRC_DEGREE -> {
                        if (scanFilter.srcSet.size == 1) {
                            label.count(scanFilter.srcSet.first(), scanFilter.dir).toRowFlux().single().map {
                                StatLong(statName, it.getLong("COUNT(1)"))
                            }
                        } else {
                            null
                        }
                    }
                    // used in HashLabel
                    StatKey.TGT_DEGREE -> {
                        if (scanFilter.tgt != null) {
                            label.count(scanFilter.tgt, Direction.IN).toRowFlux().single().map {
                                StatLong(statName, it.getLong("COUNT(1)"))
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }

        val statsMono = Flux.merge(statMonos).collectList()

        return dfMono
            .zipWith(statsMono) { a, b ->
                val selectFields =
                    if ("COUNT(1)" in scanFilter.selectFields) {
                        listOf("*")
                    } else {
                        scanFilter.selectFields
                    }
                // As a policy, otherPredicates is used in just scan.
                // .where(scanFilter.otherPredicates)
                a
                    .select(selectFields)
                    .copy(stats = b)
            }.defaultIfEmpty(DataFrame.empty)
            .subscribeOn(Schedulers.boundedElastic())
    }

    fun queryGet(
        name: EntityName,
        src: Any,
        tgt: Any,
        stats: Set<StatKey> = emptySet(),
    ): Mono<DataFrame> {
        val scanFilter =
            singleStepQuery(
                ScanFilter(
                    name = name,
                    srcSet = setOf(src),
                    tgt = setOf(tgt),
                ),
                stats,
            )
        return scanFilter
    }

    fun queryScan(
        name: EntityName,
        start: Any,
        direction: Direction = ScanFilter.defaultDir,
        indexName: String? = null,
        stats: Set<StatKey> = emptySet(),
        otherPredicates: Set<WherePredicate> = emptySet(),
        limit: Int = ScanFilter.defaultLimit,
    ): Mono<DataFrame> {
        val scanFilter =
            ScanFilter(
                name = name,
                srcSet = setOf(start),
                dir = direction,
                indexName = indexName,
                otherPredicates = otherPredicates,
                limit = limit,
            )
        return singleStepQuery(scanFilter, stats)
    }

    fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>> = queryExecutor.query(request)

    // -- system

    fun updateQueries(): Mono<Void> =
        Flux
            .fromIterable(services.keys)
            .flatMap { serviceEntityName ->
                queryDdl
                    .getAll(serviceEntityName.shiftNameToService())
                    .flatMapMany { labels -> Flux.fromIterable(labels.content) }
                    .filter(QueryEntity::active)
                    .onErrorResume { error ->
                        log.error("Error fetching queries for item: {}", serviceEntityName, error)
                        Flux.empty()
                    }
            }.collectList()
            .map { fetchedPreparedQueries ->
                queries = fetchedPreparedQueries.associateBy { it.name }
            }.onErrorResume { error ->
                log.error("Error updating queries: {}", error.message, error)
                Mono.empty()
            }.then()

    fun updateAliases(): Mono<Void> =
        Flux
            .fromIterable(services.keys)
            .flatMap { serviceEntityName ->
                aliasDdl
                    .getAll(serviceEntityName.shiftNameToService())
                    .flatMapMany { page -> Flux.fromIterable(page.content) }
                    .filter(AliasEntity::active)
                    .onErrorResume { error ->
                        log.error("Error fetching aliases for item: {}", serviceEntityName, error)
                        Flux.empty()
                    }
            }.collectList()
            .map { fetchedAliases ->
                val oldAliases = aliases.toMap()
                val newAliases =
                    fetchedAliases.map {
                        if (it.target in labels) {
                            it
                        } else {
                            if (it.name in oldAliases) {
                                log.error(
                                    "Alias {} target {} not found. Reverting to old target {}.",
                                    it.name,
                                    it.target,
                                    oldAliases[it.name],
                                )
                                it.copy(target = oldAliases[it.name]!!)
                            } else {
                                log.error(
                                    "Alias {} target {} not found. fallback to nilLabel.",
                                    it.name,
                                    it.target,
                                )
                                it.copy(target = nilLabel.name)
                            }
                        }
                    }
                aliases = newAliases.associate { it.name to it.target }
                aliasEntities = newAliases.associateBy { it.name }
            }.onErrorResume { error ->
                log.error("Error updating aliases: {}", error.message, error)
                Mono.empty()
            }.then()

    @Suppress("LongMethod")
    private fun warmUp(label: Label): Mono<Void> {
        val storage = getStorage(label.entity.storage)
        val doWarmUp =
            when (storage?.type) {
                StorageType.HBASE -> {
                    val options = storage.materialize().options as HBaseOptions
                    options.checkConnection()
                }
                else -> Mono.just(false)
            }

        val fakeEdge = label.entity.fakeEdges().first()

        val writeWarmUp =
            label
                .mutate(
                    fakeEdge.toTraceEdge(),
                    EdgeOperation.INSERT,
                    bulk = true,
                ).timeout(Duration.ofSeconds(2L))
                .flatMap {
                    if (it.status == EdgeOperationStatus.ERROR) {
                        log.error("Warm up for label {} failed.", label.name)
                        Mono.just(0L)
                    } else {
                        Flux
                            .range(1, warmUpConfig.count)
                            .flatMap({
                                label.mutate(fakeEdge.toTraceEdge(), EdgeOperation.INSERT, bulk = true)
                            }, warmUpConfig.concurrency)
                            .count()
                    }
                }.onErrorReturn(0L)

        val readWarmUp =
            label
                .get(
                    fakeEdge.src,
                    fakeEdge.tgt,
                    Direction.OUT,
                    emptySet(),
                    EmptyEdgeIdEncoder.INSTANCE,
                ).timeout(Duration.ofSeconds(2L))
                .flatMap {
                    Flux
                        .range(1, warmUpConfig.count)
                        .flatMap({
                            label.get(
                                fakeEdge.src,
                                fakeEdge.tgt,
                                Direction.OUT,
                                emptySet(),
                                EmptyEdgeIdEncoder.INSTANCE,
                            )
                        }, warmUpConfig.concurrency)
                        .count()
                }.onErrorReturn(0L)

        return doWarmUp
            .flatMap {
                if (it) {
                    Mono
                        .defer {
                            if (label.entity.readOnly) {
                                Mono.empty()
                            } else {
                                writeWarmUp
                            }
                        }.then(Mono.defer { readWarmUp })
                        .doFinally { signal ->
                            log.info("Warm up for label {} completed. signal: {}", label.name, signal)
                        }
                } else {
                    log.info("skip write warm up for label {} (storageType: {})", label.name, storage?.type)
                    Mono.empty()
                }
            }.then()
    }

    fun updateLabels(): Mono<Void> =
        Flux
            .fromIterable(services.keys)
            .flatMap { serviceEntityName ->
                labelDdl
                    .getAll(serviceEntityName.shiftNameToService())
                    .flatMapMany { labels -> Flux.fromIterable(labels.content) }
                    .filter(LabelEntity::active)
                    .onErrorResume { error ->
                        log.error("Error fetching labels for item: {}", serviceEntityName, error)
                        Flux.empty()
                    }
            }.collectList()
            .map { fetchedLabels ->
                val removedLabel = mutableListOf<Label>()
                val newLabels =
                    fetchedLabels.map { fetchedEntity ->
                        val existingLabel = labels[fetchedEntity.name]
                        if (existingLabel != null && existingLabel.entity == fetchedEntity) {
                            existingLabel to false
                        } else {
                            if (existingLabel != null) {
                                removedLabel += existingLabel
                            }
                            fetchedEntity.materialize(this) to true
                        }
                    }
                removedLabel to newLabels
            }.flatMap { (removedLabel, newLabels) ->
                val warmUpLabels = newLabels.filter { it.second }.map { it.first }
                Flux
                    .fromIterable(warmUpLabels)
                    .flatMap {
                        warmUp(it)
                    }.collectList()
                    .thenReturn(removedLabel to newLabels.map { it.first })
            }.map { (removedLabel, newLabels) ->
                val newLabelMap = newLabels.associateBy { it.name }
                labels = newLabelMap + systemLabels + predefinedLabels
                removedLabel.forEach { it.close() }
            }.onErrorResume { error ->
                log.error("Error updating labels: {}", error.message, error)
                Mono.empty()
            }.then()

    fun updateStorages(): Mono<Void> =
        storageDdl
            .getAll(EntityName.origin)
            .map {
                storages = it.content
                    .filter(StorageEntity::active)
                    .associateBy { storage -> storage.name } + systemStorages
            }.onErrorResume { error ->
                log.error("Error updating storages: {}", error.message, error)
                Mono.empty()
            }.then()

    fun updateServices(): Mono<Void> =
        serviceDdl
            .getAll(EntityName.origin)
            .map {
                services =
                    it.content
                        .filter(ServiceEntity::active)
                        .associateBy { service -> service.name }
            }.onErrorResume { error ->
                log.error("Error updating services: {}", error.message, error)
                Mono.empty()
            }.then()

    fun startMetastoreReload(
        delay: Duration,
        period: Duration,
        logger: Logger,
    ) {
        logger.info(
            "Starting Flux.interval for reloading metastore every {} ms after {} ms delay.",
            period.toMillis(),
            delay.toMillis(),
        )
        intervalDisposable =
            Flux
                .interval(delay, period)
                .onBackpressureDrop { log.warn("backpressure drop {}", it) }
                .flatMap {
                    log.debug("reloading metastore")
                    updateAllMetadata()
                        .then(wal.writeHeartBeat(Metadata.heartBeatEntityName, hostName))
                        .then(cdc.writeHeartBeat(Metadata.heartBeatEntityName, hostName))
                        .then(insertCurrentMetadata())
                }.subscribeOn(Schedulers.boundedElastic()) // Use the dedicated scheduler
                .onErrorContinue { error, _ ->
                    logger.error("Error occurred during metastore reload or unexpected error: {}. Continuing with next interval.", error.message, error)
                }.subscribe()
    }

    fun updateAllMetadata(): Mono<Void> =
        updateServices()
            .then(updateStorages())
            .then(Mono.defer { updateLabels() })
            .then(Mono.defer { updateAliases() })
            .then(Mono.defer { updateQueries() })
            .doFinally {
                metadataInitialized = true
            }

    private fun insertCurrentMetadata(): Mono<Void> {
        val onlineMetadataLabel = getLabel(Metadata.onlineMetadataLabelV2Entity.name)
        val currentTimeMs = System.currentTimeMillis()

        fun makeEdge(
            type: MetadataType,
            entity: EdgeEntity,
        ): TraceEdge {
            val hashValue = MetadataSyncStatus.normalizedHashValue(entity)
            return Edge(
                currentTimeMs,
                MetadataSyncEntity.Src(phase, type).toCompositeKey(),
                MetadataSyncEntity.Tgt(hostName, artifactInfo, entity.name).toCompositeKey(),
                MetadataSyncEntity.Props(hashValue).toMap(),
            ).toTraceEdge()
        }

        val storageEdges = storages.map { (_, entity) -> makeEdge(MetadataType.STORAGE, entity) }

        val serviceEdges = services.map { (_, entity) -> makeEdge(MetadataType.SERVICE, entity) }

        val labelEdges = labels.map { (_, label) -> makeEdge(MetadataType.LABEL, label.entity) }

        val aliasEdges = aliasEntities.map { (_, entity) -> makeEdge(MetadataType.ALIAS, entity) }

        val queryEdges = queries.map { (_, entity) -> makeEdge(MetadataType.QUERY, entity) }

        val edges = storageEdges + serviceEdges + labelEdges + aliasEdges + queryEdges

        return onlineMetadataLabel.mutate(edges, EdgeOperation.INSERT, bulk = true).then()
    }

    @Suppress("ForbiddenComment")
    private fun getOnlineMetadata(type: MetadataType): Mono<List<RowWithSchema>> {
        // TODO: use configuration or pagination
        val sufficientFetchSize = 1000
        val bound = Duration.ofMinutes(2)
        val lastTs = System.currentTimeMillis() - bound.toMillis()

        val scanFilter =
            ScanFilter(
                name = Metadata.onlineMetadataLabelV2Entity.name,
                srcSet = setOf(MetadataSyncEntity.Src(phase, type).toCompositeKey()),
                indexName = Metadata.onlineMetadataLabelV2Entity.indices[0].name,
                limit = sufficientFetchSize,
            )

        return singleStepQuery(scanFilter, emptySet())
            .map {
                it
                    .toRowWithSchema()
                    .filter { row -> row.getLong(EdgeSchema.Fields.TS) > lastTs }
            }
    }

    fun getMetadataSyncStatus(
        type: MetadataType,
        service: String? = null,
    ): Mono<MetadataSyncStatus> {
        val (onMetastore, onHosts) =
            when (type) {
                MetadataType.STORAGE -> {
                    val metadataOnMetastore = storageDdl.getAll(EntityName.origin)
                    val metadataOnHosts = getOnlineMetadata(MetadataType.STORAGE)
                    metadataOnMetastore to metadataOnHosts
                }
                MetadataType.SERVICE -> {
                    val metadataOnMetastore = serviceDdl.getAll(EntityName.origin)
                    val metadataOnHosts = getOnlineMetadata(MetadataType.SERVICE)
                    metadataOnMetastore to metadataOnHosts
                }
                MetadataType.LABEL -> {
                    requireNotNull(service) { "service should not be null for LABEL" }
                    val metadataOnMetastore = labelDdl.getAll(EntityName(service))
                    val metadataOnHosts = getOnlineMetadata(MetadataType.LABEL)
                    metadataOnMetastore to metadataOnHosts
                }
                MetadataType.ALIAS -> {
                    requireNotNull(service) { "service should not be null for ALIAS" }
                    val metadataOnMetastore = aliasDdl.getAll(EntityName(service))
                    val metadataOnHosts = getOnlineMetadata(MetadataType.ALIAS)
                    metadataOnMetastore to metadataOnHosts
                }
                MetadataType.QUERY -> {
                    requireNotNull(service) { "service should not be null for QUERY" }
                    val metadataOnMetastore = queryDdl.getAll(EntityName(service))
                    val metadataOnHosts = getOnlineMetadata(MetadataType.QUERY)
                    metadataOnMetastore to metadataOnHosts
                }
            }
        return onMetastore.zipWith(onHosts).map {
            MetadataSyncStatus.getMetadataSyncStatus(it.t1, it.t2)
        }
    }

    override fun close() {
        intervalDisposable?.dispose()
        log.info("Disposed Flux.interval for reloading metastore - {}", intervalDisposable)
        HBaseConnections.closeConnections().block()
        DefaultHBaseCluster.INSTANCE.close()
    }

    fun status(name: EntityName): Mono<String> = getLabel(name).status()

    fun getEdgeId(
        name: EntityName,
        src: String,
        tgt: String,
    ): Mono<String> = Mono.fromCallable { getLabel(name).getEdgeId(idEdgeEncoder, src, tgt) }

    fun getSrcAndTgt(edgeId: String): Pair<Any, Any> {
        val pair = idEdgeEncoder.decode(edgeId)
        return pair.key to pair.value
    }

    fun migrate(name: String): Mono<List<String>> = Migration.migrate(this, name)

    fun dumpAll(): List<String> =
        listOf("storages") +
            storages.map {
                it.toString()
            } + listOf("services") +
            services.map {
                it.toString()
            } + listOf("labels") +
            labels.map {
                it.toString()
            } + listOf("aliases") +
            aliases.map {
                it.toString()
            } + listOf("queries") +
            queries.map {
                it.toString()
            }

    companion object {
        internal val log = getLogger()

        val reactorLogger = reactor.util.Loggers.getLogger(Graph::class.java)

        @Suppress("LongMethod")
        fun create(
            config: GraphConfig,
            walFactory: WalFactory,
            cdcFactory: CdcFactory,
            kafkaClientFactory: KafkaClientFactory,
            webClientFactory: WebClientFactory,
        ): Graph {
            DefaultHBaseCluster.initialize(config.hbase)
            log.info("phase: {}", config.phase)
            log.info("tenant: {}", config.tenant)
            log.info("graph config: {}", config)
            log.info("kafkaClientFactory: {}", kafkaClientFactory)
            log.info("webClientFactory: {}", webClientFactory)

            EntityName.initialize(config.phase, config.tenant)
            WalLog.initialize(config.phase, config.tenant)
            CdcContext.initialize(config.phase, config.tenant)

            val startupTime = System.currentTimeMillis()
            val hostName = config.hostName
            val wal = walFactory.create(config.walProperties, kafkaClientFactory)
            val cdc = cdcFactory.create(config.cdcProperties, kafkaClientFactory)

            val edgeEncoderFactory =
                if (config.encoderPoolSize > 0) {
                    log.info("Creating pooled EdgeEncoderFactory with size: {}", config.encoderPoolSize)
                    EdgeEncoderFactory(config.encoderPoolSize)
                } else {
                    log.info("Creating non-pooled EdgeEncoderFactory")
                    EdgeEncoderFactory()
                }

            val edgeRecordMapper =
                run {
                    val pool = ByteArrayBufferPool.create(config.encoderPoolSize, Constants.Codec.DEFAULT_BUFFER_SIZE)
                    EdgeRecordMapper(
                        state = EdgeStateRecordMapper.create(pool),
                        index = EdgeIndexRecordMapper.create(pool),
                        count = EdgeCountRecordMapper.create(pool),
                        lock = EdgeLockRecordMapper.create(pool),
                        group = EdgeGroupRecordMapper.create(pool),
                    )
                }

            val metadataTable = config.metastoreTable?.let { MetadataTable.get(it) } ?: MetadataTable.legacy
            val localMetastore: Database = createDatabase(config, "local", metadataTable)
            val metastore: Database = createDatabase(config, "global", metadataTable)
            log.info("metadataTable: {}", metadataTable.tableName)
            val defaultHBaseStorageEntity =
                config.defaultStorageEntity?.let { entity ->
                    log.info("default hbase storage initialize with HBase type")
                    entity.toStorageEntity(EntityName.fromOrigin(Metadata.defaultHBaseStorageName))
                }

            val onlineMetadataLabelEntity =
                if (config.defaultStorageEntity != null) {
                    Metadata.onlineMetadataLabelV2Entity
                } else {
                    // fall back to NIL type
                    Metadata.onlineMetadataLabelV2Entity.copy(type = LabelType.NIL, storage = "")
                }

            val storageEntities =
                listOfNotNull(
                    Metadata.localOnlyStorageEntity,
                    Metadata.localBackedMetastoreEntity,
                    Metadata.metastoreStorageEntity,
                    defaultHBaseStorageEntity,
                ).associateBy { it.name }

            val defaults =
                AbstractGraphDefaults(
                    localMetastore,
                    metastore,
                    metadataTable,
                    edgeEncoderFactory,
                    edgeRecordMapper,
                    config.lockTimeout,
                    storageEntities,
                    DefaultHBaseCluster.INSTANCE,
                )

            val serviceLabel =
                Metadata.serviceLabelEntity.materialize(defaults) {
                    mutate(Metadata.sysServiceEntity.toEdge().toTraceEdge(), EdgeOperation.INSERT).block()
                }

            val storageLabel =
                Metadata.storageLabelEntity.materialize(defaults) {
                    mutate(Metadata.localOnlyStorageEntity.toEdge(), EdgeOperation.INSERT)
                        .then(mutate(Metadata.localBackedMetastoreEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.metastoreStorageEntity.toEdge(), EdgeOperation.INSERT))
                        .let { chain ->
                            if (defaultHBaseStorageEntity != null) {
                                chain.then(mutate(defaultHBaseStorageEntity.toEdge(), EdgeOperation.INSERT))
                            } else {
                                chain
                            }
                        }.block()
                }

            val infoLabel =
                Metadata.infoLabelEntity.materialize(defaults) {
                    val name = EntityName.fromOrigin(hostName)
                    mutate(
                        name.toTraceEdge(startupTime, mapOf("message" to "initialized at $startupTime")),
                        EdgeOperation.INSERT,
                    ).block()
                }

            val queryLabel = Metadata.queryLabelEntity.materialize(defaults)

            val aliasLabel = Metadata.aliasLabelEntity.materialize(defaults)

            val onlineMetadataLabel = onlineMetadataLabelEntity.materialize(defaults)

            val nilLabel =
                Metadata.sysNilLabelEntity.materialize(defaults) {
                    mutate(Metadata.sysNilLabelEntity.toEdge(), EdgeOperation.INSERT).block()
                }

            val labelLabel =
                Metadata.labelLabelEntity.materialize(defaults) {
                    mutate(Metadata.serviceLabelEntity.toEdge(), EdgeOperation.INSERT)
                        .then(mutate(Metadata.storageLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.labelLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.infoLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.queryLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.aliasLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(onlineMetadataLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .then(mutate(Metadata.sysNilLabelEntity.toEdge(), EdgeOperation.INSERT))
                        .block()
                }

            return Graph(
                wal,
                cdc,
                defaults.localMetastore,
                defaults.metastore,
                defaults.metadataTable,
                defaults.edgeEncoderFactory,
                defaults.edgeRecordMapper,
                defaults.datastore,
                defaults.storages,
                config,
                serviceLabel,
                storageLabel,
                labelLabel,
                infoLabel,
                queryLabel,
                aliasLabel,
                onlineMetadataLabel,
                nilLabel,
            )
        }

        private fun createDatabase(
            config: GraphConfig,
            type: String,
            metadataTable: MetadataTable,
        ): Database {
            val database =
                when (type) {
                    "local" -> {
                        Database.connect(
                            "jdbc:h2:mem:local-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL",
                            driver = "org.h2.Driver",
                        )
                    }
                    "global" -> {
                        val hikariConfig =
                            HikariConfig().apply {
                                jdbcUrl = config.metastoreUrl
                                if (config.metastoreDriver != null) {
                                    driverClassName = config.metastoreDriver
                                }
                                username = config.metastoreUser
                                password = config.metastorePassword
                                maximumPoolSize = config.metastoreConnectionPoolSize
                            }
                        log.info("hikariConfig: {}", hikariConfig)
                        val dataSource = HikariDataSource(hikariConfig)
                        Database.connect(dataSource)
                    }
                    else -> {
                        throw UnsupportedOperationException("Unsupported database type: $type")
                    }
                }
            transaction(database) {
                SchemaUtils.create(metadataTable)
            }
            return database
        }
    }
}
