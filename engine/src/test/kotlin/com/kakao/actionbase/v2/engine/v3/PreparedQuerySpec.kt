package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.query.PreparedQuery
import com.kakao.actionbase.engine.service.MetadataStatus
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.PreparedQueryService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class PreparedQuerySpec :
    StringSpec(
        {
            val database = GraphFixtures.serviceName
            val viewTable = "views"
            val mapper = jacksonObjectMapper()

            lateinit var graph: Graph
            lateinit var mutations: MutationService
            lateinit var queries: PreparedQueryService

            beforeTest {
                graph = GraphFixtures.create()
                val engine = V2BackedEngine(graph)
                mutations = MutationService(engine)
                queries = PreparedQueryService(graph, QueryService(engine))
            }

            afterTest {
                graph.close()
            }

            fun createViewTable() {
                graph.labelDdl
                    .create(
                        EntityName(database, viewTable),
                        LabelCreateRequest(
                            desc = "views",
                            type = LabelType.INDEXED,
                            schema =
                                EdgeSchema(
                                    VertexField(VertexType.STRING),
                                    VertexField(VertexType.STRING),
                                    listOf(Field("viewedAt", DataType.LONG, false)),
                                ),
                            dirType = DirectionType.OUT,
                            storage = GraphFixtures.datastoreStorage,
                            indices = listOf(Index("viewed_desc", listOf(Index.Field("viewedAt", Order.DESC)))),
                        ),
                    ).test()
                    .assertNext { it.status.name shouldBe "CREATED" }
                    .verifyComplete()
            }

            fun seed() {
                val request =
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {"viewedAt": 300}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "item2", "properties": {"viewedAt": 200}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "user2", "target": "item3", "properties": {"viewedAt": 100}}}
                      ]
                    }
                    """.trimIndent()
                mutations
                    .mutate(database, viewTable, mapper.readValue<EdgeBulkMutationRequest>(request).mutations)
                    .test()
                    .assertNext { }
                    .verifyComplete()
            }

            /** `limit` sits where an `Int` goes and `minViewedAt` sits inside SQL, so both routes are covered. */
            fun fetch(): JsonNode =
                mapper.readTree(
                    """
                    [
                      {
                        "type": "SCAN",
                        "name": "viewed",
                        "database": "$database",
                        "table": "$viewTable",
                        "index": "viewed_desc",
                        "direction": "OUT",
                        "source": {"type": "VALUE", "value": ["{entity}"]},
                        "limit": "{limit}"
                      }
                    ]
                    """.trimIndent(),
                )

            fun transform(sql: String): JsonNode =
                mapper.readTree(
                    """
                    [
                      {"type": "SQL", "name": "result", "sql": "$sql"}
                    ]
                    """.trimIndent(),
                )

            val sql = "SELECT target AS itemId, viewedAt FROM viewed WHERE viewedAt >= {minViewedAt} ORDER BY viewedAt DESC"

            fun arguments(): List<StructField> =
                listOf(
                    StructField("entity", PrimitiveType.STRING, "조회 대상", false),
                    StructField("limit", PrimitiveType.INT, "읽어올 행 수", false),
                    StructField("minViewedAt", PrimitiveType.LONG, "이 값 미만은 버림", false),
                )

            fun register() = queries.register("recent views", arguments(), fetch(), transform(sql)).block()!!

            "a registered query runs by id, with a value bound where an Int goes and inside SQL" {
                createViewTable()
                seed()

                val registered = register()
                registered.arguments.map { it.name } shouldBe listOf("entity", "limit", "minViewedAt")
                registered.active shouldBe true

                queries
                    .query(registered.id, mapOf("entity" to "user1", "limit" to 10, "minViewedAt" to 0))
                    .test()
                    .assertNext { result ->
                        result.keys shouldBe setOf("result")
                        result.getValue("result").rows.map { listOf(it.data["itemId"], it.data["viewedAt"]) } shouldBe
                            listOf(listOf("item1", 300L), listOf("item2", 200L))
                    }.verifyComplete()

                // The same registration, a different bound value: the SQL text never changed.
                queries
                    .query(registered.id, mapOf("entity" to "user1", "limit" to 10, "minViewedAt" to 250))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data["itemId"] } shouldBe listOf("item1")
                    }.verifyComplete()
            }

            "a value of the wrong type is read as the type the registration declared" {
                createViewTable()
                seed()

                val registered = register()

                queries
                    .query(registered.id, mapOf("entity" to "user1", "limit" to "10", "minViewedAt" to "250"))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data["itemId"] } shouldBe listOf("item1")
                    }.verifyComplete()
            }

            "a body that uses a name it does not declare is refused" {
                createViewTable()

                shouldThrow<IllegalArgumentException> {
                    queries
                        .register(
                            "missing declarations",
                            listOf(StructField("entity", PrimitiveType.STRING, "", false)),
                            fetch(),
                            transform(sql),
                        ).block()
                }
            }

            /**
             * A type that reads as the slot's own is accepted, even when it is not the same type: Jackson
             * coerces `"1"` into the `Int` that `limit` wants, so declaring `STRING` there is not an error
             * and does not have to be treated as one.
             */
            "a declared type only has to read back as the slot's own" {
                createViewTable()
                seed()

                val registered =
                    queries
                        .register(
                            "limit declared as text",
                            listOf(
                                StructField("entity", PrimitiveType.STRING, "", false),
                                StructField("limit", PrimitiveType.STRING, "", false),
                                StructField("minViewedAt", PrimitiveType.LONG, "", false),
                            ),
                            fetch(),
                            transform(sql),
                        ).block()!!

                queries
                    .query(registered.id, mapOf("entity" to "user1", "limit" to 10, "minViewedAt" to 250))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data["itemId"] } shouldBe listOf("item1")
                    }.verifyComplete()
            }

            "a name resolves to the query it points at, and the query is readable either way" {
                createViewTable()
                seed()

                val registered = register()

                queries
                    .createAlias("recent_views", "홈 화면", registered.id)
                    .test()
                    .assertNext { it.target shouldBe registered.id }
                    .verifyComplete()

                queries
                    .get("recent_views")
                    .test()
                    .assertNext { it.id shouldBe registered.id }
                    .verifyComplete()

                queries
                    .query("recent_views", mapOf("entity" to "user2", "limit" to 10, "minViewedAt" to 0))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data["itemId"] } shouldBe listOf("item3")
                    }.verifyComplete()
            }

            "a name can be moved to another query and back" {
                createViewTable()
                seed()

                val first = register()
                val second = queries.register("only the id", arguments(), fetch(), transform(sql)).block()!!

                queries.createAlias("recent_views", "홈 화면", first.id).block()

                queries
                    .updateAlias("recent_views", target = second.id)
                    .test()
                    .assertNext { it.target shouldBe second.id }
                    .verifyComplete()

                queries
                    .updateAlias("recent_views", target = first.id)
                    .test()
                    .assertNext { it.target shouldBe first.id }
                    .verifyComplete()
            }

            "a query a name still points at cannot be dropped" {
                createViewTable()

                val registered = register()
                queries.createAlias("recent_views", "홈 화면", registered.id).block()

                shouldThrow<IllegalArgumentException> { queries.delete(registered.id).block() }

                queries.deleteAlias("recent_views").test().verifyComplete()
                queries.delete(registered.id).test().verifyComplete()

                shouldThrow<NoSuchElementException> { queries.get(registered.id).block() }
                queries
                    .list()
                    .test()
                    .assertNext { it shouldBe emptyList() }
                    .verifyComplete()
            }

            /**
             * The alias scan stops at `metadataFetchLimit` without saying so, so a page filled to it cannot
             * show that nothing names the query. Here the one alias the scan returns names a different query,
             * which read literally would clear the delete — the names it could not reach are the point.
             */
            "a query is not dropped on an alias scan that may be truncated" {
                val truncating = GraphFixtures.create(GraphConfig.Builder().withMetadataFetchLimit(1), withTestData = false)
                try {
                    val service = PreparedQueryService(truncating, QueryService(V2BackedEngine(truncating)))
                    val named = service.register("named", arguments(), fetch(), transform(sql)).block()!!
                    val other = service.register("other", arguments(), fetch(), transform(sql)).block()!!
                    service.createAlias("recent_views", "홈 화면", named.id).block()

                    shouldThrow<IllegalStateException> { service.delete(other.id).block() }
                } finally {
                    truncating.close()
                }
            }

            "replacing the transform leaves the fetch steps as they were" {
                createViewTable()
                seed()

                val registered = register()
                val amended = "SELECT target AS itemId FROM viewed WHERE viewedAt >= {minViewedAt}"

                queries
                    .amend(registered.id, transform = transform(amended))
                    .test()
                    .assertNext { it.fetch shouldBe registered.fetch }
                    .verifyComplete()

                queries
                    .query(registered.id, mapOf("entity" to "user1", "limit" to 10, "minViewedAt" to 0))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data.keys.toList() } shouldBe listOf(listOf("itemId"), listOf("itemId"))
                    }.verifyComplete()
            }

            "listing answers with what the status asks for" {
                createViewTable()

                val registered = register()

                queries
                    .list()
                    .test()
                    .assertNext { it.map { query -> query.id } shouldBe listOf(registered.id) }
                    .verifyComplete()
                queries
                    .list(MetadataStatus.INACTIVE)
                    .test()
                    .assertNext { it shouldBe emptyList() }
                    .verifyComplete()
                queries
                    .aliases()
                    .test()
                    .assertNext { it shouldBe emptyList() }
                    .verifyComplete()
            }

            "calling a query nobody registered says so" {
                createViewTable()

                shouldThrow<NoSuchElementException> {
                    queries.query("no_such_query", mapOf("entity" to "user1")).block()
                }
            }

            /** The same body run without being registered: values come in the call, with no declared types. */
            "a query sent whole runs with the values sent alongside it" {
                createViewTable()
                seed()

                val prepared = PreparedQuery.adhoc(fetch(), transform(sql), emptySet())
                prepared.parameters shouldBe setOf("entity", "limit", "minViewedAt")

                QueryService(V2BackedEngine(graph))
                    .query(prepared, mapOf("entity" to "user1", "limit" to 10, "minViewedAt" to 250))
                    .test()
                    .assertNext { result ->
                        result.getValue("result").rows.map { it.data["itemId"] } shouldBe listOf("item1")
                    }.verifyComplete()
            }
        },
    )
