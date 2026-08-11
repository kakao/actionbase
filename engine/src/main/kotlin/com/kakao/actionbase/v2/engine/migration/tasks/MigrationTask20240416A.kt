package com.kakao.actionbase.v2.engine.migration.tasks

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object MigrationTask20240416A : MigrationTask {
    private val oldOrigin = Metadata.origin
    private val serviceLabelName = EntityName(Metadata.sysServiceName, Metadata.sysServiceLabelName)
    private val storageLabelName = EntityName(Metadata.sysServiceName, Metadata.sysStorageLabelName)
    private val labelLabelName = EntityName(Metadata.sysServiceName, Metadata.sysLabelLabelName)
    private val aliasLabelName = EntityName(Metadata.sysServiceName, Metadata.sysAliasLabelName)

    // {phase}: added to all src
    // ex):
    //  origin -> {phase}:origin
    //  {service} -> {phase}:service
    override fun migrate(graph: Graph): Mono<List<String>> {
        val t1 = migrateServices(graph)
        val t2 = migrateStorages(graph)
        val t3 = migrateLabels(graph)
        val t4 = migrateAliases(graph)

        return Mono
            .zip(t1, t2, t3, t4)
            .map {
                it.t1 + it.t2 + it.t3 + it.t4
            }
    }

    private fun migrateServices(graph: Graph): Mono<List<String>> {
        val oldServiceFilter =
            ScanFilter(
                serviceLabelName,
                srcSet = setOf(oldOrigin),
                limit = 1000,
            )

        return graph
            .singleStepQuery(oldServiceFilter)
            .map {
                ServiceEntity.fromDataFrame(it)
            }.flatMapMany {
                Flux
                    .fromIterable(it)
                    .flatMap { serviceEntity ->
                        graph.serviceDdl.create(serviceEntity.name, serviceEntity.toCreateRequest())
                    }
            }.map {
                "service / ${it.result?.fullName} / ${it.status}"
            }.collectList()
    }

    private fun migrateStorages(graph: Graph): Mono<List<String>> {
        val oldStorageFilter =
            ScanFilter(
                storageLabelName,
                srcSet = setOf(oldOrigin),
                limit = 1000,
            )

        return graph
            .singleStepQuery(oldStorageFilter)
            .map {
                StorageEntity.fromDataFrame(it)
            }.flatMapMany {
                Flux
                    .fromIterable(it)
                    .flatMap { storageEntity ->
                        graph.storageDdl.create(storageEntity.name, storageEntity.toCreateRequest())
                    }
            }.map {
                "storage / ${it.result?.fullName} / ${it.status}"
            }.collectList()
    }

    private fun migrateOnService(
        graph: Graph,
        block: (Graph, ServiceEntity) -> Mono<List<String>>,
    ): Mono<List<String>> {
        val oldServiceFilter =
            ScanFilter(
                serviceLabelName,
                srcSet = setOf(oldOrigin),
                limit = 1000,
            )

        return graph
            .singleStepQuery(oldServiceFilter)
            .map {
                ServiceEntity.fromDataFrame(it)
            }.flatMapMany {
                Flux
                    .fromIterable(it)
                    .flatMap { serviceEntity ->
                        block(graph, serviceEntity)
                    }
            }.flatMap {
                Flux.fromIterable(it)
            }.collectList()
    }

    private fun migrateLabels(graph: Graph): Mono<List<String>> = migrateOnService(graph, ::migrateLabelsOfService)

    private fun migrateLabelsOfService(
        graph: Graph,
        serviceEntity: ServiceEntity,
    ): Mono<List<String>> {
        val oldLabelFilter =
            ScanFilter(
                labelLabelName,
                srcSet = setOf(serviceEntity.name.nameNotNull),
                limit = 1000,
            )

        return graph
            .singleStepQuery(oldLabelFilter)
            .map {
                LabelEntity.fromDataFrame(it)
            }.flatMapMany {
                Flux
                    .fromIterable(it)
                    .flatMap { labelEntity ->
                        graph.labelDdl.create(labelEntity.name, labelEntity.toCreateRequest())
                    }
            }.map {
                "label / ${it.result?.fullName} / ${it.status}"
            }.collectList()
    }

    private fun migrateAliases(graph: Graph): Mono<List<String>> = migrateOnService(graph, ::migrateAliasesOfService)

    private fun migrateAliasesOfService(
        graph: Graph,
        serviceEntity: ServiceEntity,
    ): Mono<List<String>> {
        val oldAliasFilter =
            ScanFilter(
                aliasLabelName,
                srcSet = setOf(serviceEntity.name.nameNotNull),
                limit = 1000,
            )

        println(oldAliasFilter)

        return graph
            .singleStepQuery(oldAliasFilter)
            .map {
                AliasEntity.fromDataFrame(it)
            }.flatMapMany {
                Flux
                    .fromIterable(it)
                    .flatMap { aliasEntity ->
                        graph.aliasDdl.create(aliasEntity.name, aliasEntity.toCreateRequest())
                    }
            }.map {
                "alias / ${it.result?.fullName} / ${it.status}"
            }.collectList()
    }
}
