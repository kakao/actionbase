package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.metadata.common.DirectionType as V3DirectionType

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class EdgeAggQuerySpec :
    StringSpec({
        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryService: QueryService

        beforeTest {
            graph = GraphFixtures.create()
            val engine = V2BackedEngine(graph)
            mutationService = MutationService(engine)
            queryService = QueryService(engine)
        }

        afterTest {
            graph.close()
        }

        /**
         * Mutation:
         * | source | target | createdAt |
         * |--------|--------|-----------|
         * | 1000   | 2000   | 100       |
         * | 1000   | 2001   | 100       |
         * | 1000   | 2002   | 200       |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT)
         * |       row key        | qualifier (Long) | value |
         * |----------------------|------------------|-------|
         * | hash|1000|T|-5|OUT|G | createdAt=100    |     2 |
         * | hash|1000|T|-5|OUT|G | createdAt=200    |     1 |
         *
         * Before #227: predicate "100"/"200" encoded as String → count=0.
         */
        "INSERT → agg Eq on Long field returns matching count" {
            val database = GraphFixtures.serviceName
            val table = "agg_long_eq"
            val groupName = "by_created_at"

            val createRequest =
                LabelCreateRequest(
                    desc = "agg long eq test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields = listOf(Group.Field("createdAt")),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "na", "createdAt": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse.from(response).results.size shouldBe 3
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "createdAt:eq:100")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 2L
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "createdAt:eq:200")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 1L
                }.verifyComplete()
        }

        /**
         * Mutation:
         * | source | target | createdAt |
         * |--------|--------|-----------|
         * | 1000   | 2000   | 50        |
         * | 1000   | 2001   | 100       |
         * | 1000   | 2002   | 150       |
         * | 1000   | 2003   | 250       |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT)
         * |       row key        | qualifier (Long) | value |
         * |----------------------|------------------|-------|
         * | hash|1000|T|-5|OUT|G | createdAt=50     |     1 |
         * | hash|1000|T|-5|OUT|G | createdAt=100    |     1 |
         * | hash|1000|T|-5|OUT|G | createdAt=150    |     1 |
         * | hash|1000|T|-5|OUT|G | createdAt=250    |     1 |
         *
         * Range [100, 200] matches 100 and 150 → sum=2.
         */
        "INSERT → agg Between on Long field returns count in range" {
            val database = GraphFixtures.serviceName
            val table = "agg_long_bt"
            val groupName = "by_created_at"

            val createRequest =
                LabelCreateRequest(
                    desc = "agg long bt test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields = listOf(Group.Field("createdAt")),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 50}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "na", "createdAt": 150}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2003", "properties": {"permission": "na", "createdAt": 250}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse.from(response).results.size shouldBe 4
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "createdAt:bt:100,200")
                .test()
                .assertNext { payload ->
                    payload.groups.sumOf { it.value } shouldBe 2L
                }.verifyComplete()
        }

        /**
         * Regression guard: String fields already worked before #227.
         *
         * Mutation:
         * | source | target | permission |
         * |--------|--------|------------|
         * | 1000   | 2000   | me         |
         * | 1000   | 2001   | me         |
         * | 1000   | 2002   | others     |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT)
         * |       row key        | qualifier (String) | value |
         * |----------------------|--------------------|-------|
         * | hash|1000|T|-5|OUT|G | permission=me      |     2 |
         * | hash|1000|T|-5|OUT|G | permission=others  |     1 |
         */
        "INSERT → agg Eq on String field returns matching count" {
            val database = GraphFixtures.serviceName
            val table = "agg_string_eq"
            val groupName = "by_permission"

            val createRequest =
                LabelCreateRequest(
                    desc = "agg string eq test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields = listOf(Group.Field("permission")),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "me", "createdAt": 1}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "me", "createdAt": 2}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "others", "createdAt": 3}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse.from(response).results.size shouldBe 3
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "permission:eq:me")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 2L
                }.verifyComplete()
        }

        /**
         * Multi-field group: leading Long field must be cast, trailing String field
         * flows through as-is. `eqValues` (the leading-fields cast in agg()) was the
         * second copy of the buggy pattern before #227.
         *
         * Mutation:
         * | source | target | createdAt | permission |
         * |--------|--------|-----------|------------|
         * | 1000   | 2000   | 100       | me         |
         * | 1000   | 2001   | 100       | me         |
         * | 1000   | 2002   | 100       | others     |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT)
         * |       row key        | qualifier                     | value |
         * |----------------------|-------------------------------|-------|
         * | hash|1000|T|-5|OUT|G | Long(100), String("me")       |     2 |
         * | hash|1000|T|-5|OUT|G | Long(100), String("others")   |     1 |
         */
        "INSERT → agg with leading Long field and trailing String field" {
            val database = GraphFixtures.serviceName
            val table = "agg_multi_field"
            val groupName = "by_created_at_and_permission"

            val createRequest =
                LabelCreateRequest(
                    desc = "agg multi-field test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields = listOf(Group.Field("createdAt"), Group.Field("permission")),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "me", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "me", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "others", "createdAt": 100}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse.from(response).results.size shouldBe 3
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "createdAt:eq:100;permission:eq:me")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 2L
                }.verifyComplete()
        }

        /**
         * Bucketed field: `bucketOrGet` normalises the value on both paths, so no
         * schema-type cast is applied even though the underlying property is Long.
         * The query key is the bucket's `name`, not the property name.
         *
         * Mutation (createdAt is epoch millis, bucketed to yyyy-MM-dd UTC):
         * | source | target | createdAt (ms)       | → bucket day |
         * |--------|--------|----------------------|--------------|
         * | 1000   | 2000   | 1704067200000 (2024-01-01) | 2024-01-01 |
         * | 1000   | 2001   | 1704153600000 (2024-01-02) | 2024-01-02 |
         * | 1000   | 2002   | 1704240000000 (2024-01-03) | 2024-01-03 |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT)
         * |       row key        | qualifier            | value |
         * |----------------------|----------------------|-------|
         * | hash|1000|T|-5|OUT|G | String("2024-01-01") |     1 |
         * | hash|1000|T|-5|OUT|G | String("2024-01-02") |     1 |
         * | hash|1000|T|-5|OUT|G | String("2024-01-03") |     1 |
         */
        "INSERT → agg on Date-bucketed field returns matching count" {
            val database = GraphFixtures.serviceName
            val table = "agg_bucketed"
            val groupName = "by_day"

            val createRequest =
                LabelCreateRequest(
                    desc = "agg bucketed test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields =
                                    listOf(
                                        Group.Field(
                                            name = "createdAt",
                                            bucket =
                                                Bucket.Date(
                                                    name = "day",
                                                    unit = Bucket.ValueUnit.MILLISECOND,
                                                    timezone = "UTC",
                                                    format = "yyyy-MM-dd",
                                                ),
                                        ),
                                    ),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 1704067200000}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 1704153600000}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "na", "createdAt": 1704240000000}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse.from(response).results.size shouldBe 3
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "day:eq:2024-01-02")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 1L
                }.verifyComplete()
        }

        /**
         * MULTI_EDGE promotes the top-level source/target fields into the persistence
         * layer under `_source` / `_target`. Grouping on those keys is a common top-k
         * pattern (per-entity ranking by target). The write path already stores them as
         * the top-level primitive type; the read path must cast predicates the same way,
         * otherwise the qualifier bytes mismatch and the GET returns no records.
         *
         * Mutation:
         * | id | source | target |
         * |----|--------|--------|
         * | 1  | 1000   | 2000   |
         * | 2  | 1000   | 2000   |
         * | 3  | 1000   | 2001   |
         *
         * EdgeGroup (source=1000, OUT, GroupType.COUNT, fields=[_target])
         * |       row key        | qualifier (Long) | value |
         * |----------------------|------------------|-------|
         * | hash|1000|T|-5|OUT|G | _target=2000     |     2 |
         * | hash|1000|T|-5|OUT|G | _target=2001     |     1 |
         */
        "INSERT MultiEdge → agg on `_target` group returns per-target count" {
            val database = GraphFixtures.serviceName
            val table = "agg_multi_edge_target"
            val groupName = "by_target"

            val multiEdgeSchema =
                EdgeSchema(
                    VertexField(VertexType.LONG),
                    VertexField(VertexType.LONG),
                    listOf(
                        Field("_id", DataType.LONG, false),
                    ),
                )

            val createRequest =
                LabelCreateRequest(
                    desc = "multi edge target agg test",
                    type = LabelType.MULTI_EDGE,
                    schema = multiEdgeSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    readOnly = true,
                    groups =
                        listOf(
                            Group(
                                group = groupName,
                                type = GroupType.COUNT,
                                fields = listOf(Group.Field("_target")),
                                directionType = V3DirectionType.OUT,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<MultiEdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": 1000, "target": 2000, "properties": {}}},
                        {"type": "INSERT", "edge": {"version": 1, "id": 2, "source": 1000, "target": 2000, "properties": {}}},
                        {"type": "INSERT", "edge": {"version": 1, "id": 3, "source": 1000, "target": 2001, "properties": {}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { }
                .verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "_target:eq:2000")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 2L
                }.verifyComplete()

            queryService
                .agg(database, table, groupName, listOf("1000"), Direction.OUT, ranges = "_target:eq:2001")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.groups[0].value shouldBe 1L
                }.verifyComplete()
        }
    }) {
    companion object {
        val mapper = jacksonObjectMapper()
    }
}
