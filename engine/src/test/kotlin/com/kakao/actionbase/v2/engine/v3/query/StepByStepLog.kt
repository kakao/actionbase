package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.show
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.util.getLogger

import io.kotest.core.spec.style.StringSpec

class StepByStepLog :
    StringSpec({

        lateinit var graph: Graph
        val entityName = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed)

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "step-by-step" {
            logger.info("!!!!!!!!!!!!!!!!!!!! step 1")
            val scanFilter = ScanFilter(entityName, setOf(100), indexName = "permission_created_at_desc")
            graph
                .singleStepQuery(scanFilter, emptySet())
                .log(Graph.reactorLogger)
                .show()
        }
    }) {
    companion object {
        val logger = getLogger()
    }
}
