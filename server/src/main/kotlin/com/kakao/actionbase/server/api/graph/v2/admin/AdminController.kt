package com.kakao.actionbase.server.api.graph.v2.admin

import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.sync.MetadataSyncStatus
import com.kakao.actionbase.v2.engine.metadata.sync.MetadataType
import com.kakao.actionbase.v2.engine.service.ddl.AliasDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.StorageDeleteRequest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@RestController
class AdminController(
    val graph: Graph,
) {
    @GetMapping("/graph/v2/admin/labels")
    fun getAllLabels(): Map<String, String> = graph.getAllLabels()

    @GetMapping("/graph/v2/admin//migration/{name}")
    fun migrate(
        @PathVariable name: String,
    ): Mono<List<String>> = graph.migrate(name)

    @GetMapping("/graph/v2/admin/dump")
    fun dump(): Mono<List<String>> = graph.dumpAll().toMono()

    @DeleteMapping("/graph/v2/admin/service/{service}/label/{label}")
    fun deleteLabel(
        @PathVariable service: String,
        @PathVariable label: String,
    ): Mono<ResponseEntity<DdlStatus<LabelEntity>>> {
        val name = EntityName(service, label)
        val request = LabelDeleteRequest()
        return graph.labelDdl.delete(name, request).mapToResponseEntity()
    }

    @DeleteMapping("/graph/v2/admin/service/{service}/alias/{alias}")
    fun deleteAlias(
        @PathVariable service: String,
        @PathVariable alias: String,
    ): Mono<ResponseEntity<DdlStatus<AliasEntity>>> {
        val name = EntityName(service, alias)
        val request = AliasDeleteRequest()
        return graph.aliasDdl.delete(name, request).mapToResponseEntity()
    }

    @DeleteMapping("/graph/v2/admin/storage/{storage}")
    fun deleteStorage(
        @PathVariable storage: String,
    ): Mono<ResponseEntity<DdlStatus<StorageEntity>>> {
        val name = EntityName.fromOrigin(storage)
        val request = StorageDeleteRequest()
        return graph.storageDdl.delete(name, request).mapToResponseEntity()
    }

    @DeleteMapping("/graph/v2/admin/service/{service}")
    fun deleteService(
        @PathVariable service: String,
    ): Mono<ResponseEntity<DdlStatus<ServiceEntity>>> {
        val name = EntityName.fromOrigin(service)
        val request = ServiceDeleteRequest()

        return graph.labelDdl
            .getAll(EntityName(service))
            .map {
                it.content
                    .filter { entity -> entity.active }
            }.flatMap {
                if (it.isNotEmpty()) {
                    val labelNames = it.joinToString(",") { entity -> entity.fullName }
                    Mono.error(IllegalArgumentException("Service $service has active labels: $labelNames"))
                } else {
                    graph.serviceDdl.delete(name, request).mapToResponseEntity()
                }
            }
    }

    @GetMapping("/graph/v2/admin/metadata/service")
    fun getServiceSyncStatus(): Mono<MetadataSyncStatus> = graph.getMetadataSyncStatus(MetadataType.SERVICE)

    @GetMapping("/graph/v2/admin/metadata/storage")
    fun getStorageSyncStatus(): Mono<MetadataSyncStatus> = graph.getMetadataSyncStatus(MetadataType.STORAGE)

    @GetMapping("/graph/v2/admin/metadata/service/{service}/label")
    fun getLabelSyncStatus(
        @PathVariable service: String,
    ): Mono<MetadataSyncStatus> = graph.getMetadataSyncStatus(MetadataType.LABEL, service)

    @GetMapping("/graph/v2/admin/metadata/service/{service}/alias")
    fun getAliasSyncStatus(
        @PathVariable service: String,
    ): Mono<MetadataSyncStatus> = graph.getMetadataSyncStatus(MetadataType.ALIAS, service)
}
