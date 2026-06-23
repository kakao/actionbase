package com.kakao.actionbase.v2.engine.label.scan

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class IndexFilterSpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var label: HBaseIndexedLabel

        beforeSpec {
            graph = GraphFixtures.create()
            label = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed)) as HBaseIndexedLabel
        }

        "URL: src=100&index=permission_created_at_desc" {
            // SQL: src = 100 order by permission_created_at_desc
            ScanFilter(label.entity.name, setOf(100), indexName = "permission_created_at_desc").let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().map { row -> row.getOrNull("permission") } shouldContainExactly
                            listOf(
                                "me",
                                "me",
                                "na",
                                "na",
                                "others",
                                "others",
                            )
                    }.verifyComplete()
            }
        }

        "URL: src=100&permission=me&index=permission_created_at_desc" {
            // SQL: src = 100 and permission = 'me' order by permission_created_at_desc
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "permission_created_at_desc",
                otherPredicates = setOf(WherePredicate.Eq("permission", "me")),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().map { row -> row.getOrNull("permission") } shouldContainExactly
                            listOf(
                                "me",
                                "me",
                            )
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc" {
            // SQL: src = 100 order by created_at_desc
            ScanFilter(label.entity.name, setOf(100), indexName = "created_at_desc").let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().map { row -> row.getOrNull("permission") } shouldContainExactly
                            listOf(
                                "na",
                                "me",
                                "others",
                                "me",
                                "others",
                                "na",
                            )
                    }.verifyComplete()
            }
        }

        "URL: src=100,101&index=created_at_desc&limit=2" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(label.entity.name, setOf(100, 101), limit = 2, indexName = "created_at_desc").let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().map { row -> row.getOrNull("permission") } shouldContainExactly
                            listOf(
                                // for 100
                                "na",
                                "me",
                                // for 101
                                "na",
                                "me",
                            )
                    }.verifyComplete()
            }
        }

        "URL:src=100&index=created_at_desc&limit=2 with offset" {
            // SQL: src = 100 order by created_at_desc limit 2
            val offsets =
                ScanFilter(
                    label.entity.name,
                    setOf(100),
                    limit = 2,
                    indexName = "created_at_desc",
                ).let { scanFilter ->
                    label.scan(scanFilter, emptySet()).map { it.offsets }.block()!!
                }

            val offset = offsets.singleOrNull()

            ScanFilter(
                label.entity.name,
                setOf(100),
                limit = 2,
                offset = offset,
                indexName = "created_at_desc",
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().map { row -> row.getOrNull("permission") } shouldContainExactly
                            listOf(
                                "others",
                                "me",
                            )
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:gt:12" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Gt("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 15
                        createdAtList.min() shouldBe 13
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:gte:12" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Gte("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 15
                        createdAtList.min() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:bt:12,14" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Between("createdAt", 12, 14)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().forEach { println(it) }
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 14
                        createdAtList.min() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:lte:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Lte("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.forEach { println(it) }
                        createdAtList.min() shouldBe 10
                        createdAtList.max() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:lt:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Lt("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.min() shouldBe 10
                        createdAtList.max() shouldBe 11
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_desc&filter=createdAt:eq:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_desc",
                otherPredicates = setOf(WherePredicate.Eq("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.min() shouldBe 12
                        createdAtList.max() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:gt:12" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Gt("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 15
                        createdAtList.min() shouldBe 13
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:gte:12" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Gte("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 15
                        createdAtList.min() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:bt:12,14" {
            // SQL: src = 100 order by created_at_desc limit 2
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Between("createdAt", 12, 14)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        it.toRowWithSchema().forEach { println(it) }
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.max() shouldBe 14
                        createdAtList.min() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:lte:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Lte("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.min() shouldBe 10
                        createdAtList.max() shouldBe 12
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:lt:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Lt("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.min() shouldBe 10
                        createdAtList.max() shouldBe 11
                    }.verifyComplete()
            }
        }

        "URL: src=100&index=created_at_asc&filter=createdAt:eq:12" {
            ScanFilter(
                label.entity.name,
                setOf(100),
                indexName = "created_at_asc",
                otherPredicates = setOf(WherePredicate.Eq("createdAt", 12)),
            ).let { scanFilter ->
                label
                    .scan(scanFilter, emptySet())
                    .test()
                    .assertNext {
                        val createdAtList = it.toRowWithSchema().map { it["createdAt"] } as List<Long>
                        createdAtList.min() shouldBe 12
                        createdAtList.max() shouldBe 12
                    }.verifyComplete()
            }
        }
    })
