package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * Verifies the in-memory ByteArray-backed local store inside [LocalBackedJdbcHashLabel] behaves
 * like the H2-backed local store it replaced: writes in local-store mode round-trip through
 * getSelf/get, and indexName=null scans (as issued by DdlService.getAll) resolve via the
 * synthetic __default__ prefix-scan index.
 */
class LocalBackedJdbcHashLabelSpec :
    StringSpec({

        lateinit var graph: Graph

        // serviceLabelEntity is a HASH label on the local-backed metastore, so
        // graph.getLabel returns a LocalBackedJdbcHashLabel in local-store mode.
        fun localLabel(): LocalBackedJdbcHashLabel = graph.getLabel(Metadata.serviceLabelEntity.name) as LocalBackedJdbcHashLabel

        // (origin -> serviceName) edge with the required non-null `desc` prop.
        fun serviceEdge(
            serviceName: String,
            desc: String,
        ): TraceEdge = Edge(1000L, Metadata.origin, serviceName, mapOf("desc" to desc)).toTraceEdge()

        beforeTest {
            graph = GraphFixtures.create(withTestData = false)
        }

        afterTest {
            graph.close()
        }

        "write to the local store round-trips through get" {
            val label = localLabel()
            val edge = serviceEdge("parity_probe", "round-trip")

            label
                .mutate(listOf(edge), EdgeOperation.INSERT)
                .then(label.get(Metadata.origin, "parity_probe", Direction.OUT, emptySet()))
                .toRowFlux()
                .map { it["tgt"] }
                .collectList()
                .test()
                .assertNext { it shouldBe listOf("parity_probe") }
                .verifyComplete()
        }

        "indexName=null scan resolves via the __default__ index" {
            val label = localLabel()
            val edge = serviceEdge("scan_probe", "scan-route")

            // ScanFilter with no indexName mirrors DdlService.getAll(); the local indexed store
            // needs the __default__ index, so this asserts the routing in scan() works.
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(Metadata.origin),
                    dir = Direction.OUT,
                    limit = 100,
                )

            label
                .mutate(listOf(edge), EdgeOperation.INSERT)
                .then(label.scan(scanFilter, emptySet()))
                .toRowFlux()
                .map { it["tgt"] }
                .collectList()
                .test()
                .assertNext { it shouldBe listOf("scan_probe") }
                .verifyComplete()
        }
    })
