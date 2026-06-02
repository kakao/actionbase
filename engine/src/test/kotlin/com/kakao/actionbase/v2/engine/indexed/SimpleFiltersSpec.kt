package com.kakao.actionbase.v2.engine.indexed

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.GraphFixtures.serviceName

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 *  | Token       | Value                                                                               | description                                |
 *  | ------------| ----------------------------------------------------------------------------------- | ------------------------------------------ |
 *  | `<P>`       | \\xC3\\x85\\x03\\xDE,\\x80\\x00\\x00\\x00\\x00\\x00\\x00d+\\xDA\\x97\\x0EY)|)\\x82+ | hash(salt) + source + label ID             |
 *  | `<INDEX_1>` | i\\xB73\\xC7                                                                        | created_at_desc index ID                   |
 *  | `<INDEX_2>` | \\x8Dl\\xDAG4                                                                       | permission_created_at_desc index ID        |
 *  | `<VALUE_1>` | \\xD3\\x7F\\xFF\\xFF\\xFF\\xFF\\xFF\\xFF\\xF2                                       | `Long.MAX_VALUE – 14` (`createdAt >= 14`) |
 *  | `me\\x00`   | me\\x00                                                                             | "me" with the null term                    |
 *  | `me\\x01`   | me\\x01                                                                             | "me" with the null term + 1                |
 *
 *  # createdAt >= 14
 *
 *  startRow: <P><INDEX_1>,
 *  stopRow: <P><INDEX_1><T>
 *
 * # permission = me
 *
 *  startRow: <P><INDEX_2>me\\x00,
 *  stopRow: <P><INDEX_2>me\\x01
 *
 * # permission = me and createdAt >= 14
 *
 *  startRow: <P><INDEX_2>me\\x00,
 *  stopRow: <P><INDEX_2>me\\x00<T>
 */
class SimpleFiltersSpec :
    StringSpec({

        lateinit var graph: Graph

        val labelName = EntityName(serviceName, GraphFixtures.hbaseIndexed)

        val src = "100"
        val permission = "me"
        val createdAtBound = "14"
        val createdAtOperator = "gte"
        val singleItemIndex = "created_at_desc"
        val multipleItemIndex = "permission_created_at_desc"
        val singlePermissionIsMe = "permission:eq:$permission"
        val singleCreatedAtIsGte = "createdAt:$createdAtOperator:$createdAtBound"
        val multiplePermissionIsMeAndCreatedAtIsGte = "$singlePermissionIsMe;$singleCreatedAtIsGte"

        val singlePermissionIsMeCondition: Set<WherePredicate> = WherePredicate.parse(singlePermissionIsMe).toSet()
        val singleCreatedAtIsGteCondition: Set<WherePredicate> = WherePredicate.parse(singleCreatedAtIsGte).toSet()
        val multiplePermissionIsMeAndCreatedAtIsGteCondition: Set<WherePredicate> =
            WherePredicate
                .parse(
                    multiplePermissionIsMeAndCreatedAtIsGte,
                ).toSet()

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "parsing" {
            singlePermissionIsMeCondition shouldBe setOf(WherePredicate.Eq("permission", "me"))
            singleCreatedAtIsGteCondition shouldBe setOf(WherePredicate.Gte("createdAt", createdAtBound))
            multiplePermissionIsMeAndCreatedAtIsGteCondition shouldBe setOf(WherePredicate.Eq("permission", "me"), WherePredicate.Gte("createdAt", createdAtBound))
        }

        "single condition on the index with a single item: $singleCreatedAtIsGte" {
            val label = graph.getLabel(labelName)
            val scanFilter =
                ScanFilter(
                    name = labelName,
                    srcSet = setOf(src),
                    indexName = singleItemIndex,
                    otherPredicates = singleCreatedAtIsGteCondition,
                )

            label
                .scan(scanFilter, emptySet())
                .test()
                .assertNext {
                    it.rows.map { row -> row.array.toList() } shouldBe
                        /**
                         * X Edge(10, 100, 1000, mapOf("permission" to "na", "createdAt" to 10)),
                         * X Edge(11, 100, 1001, mapOf("permission" to "others", "createdAt" to 11)),
                         * X Edge(12, 100, 1002, mapOf("permission" to "me", "createdAt" to 12)),
                         * X Edge(13, 100, 1003, mapOf("permission" to "others", "createdAt" to 13)),
                         * O Edge(14, 100, 1004, mapOf("permission" to "me", "createdAt" to 14)),
                         * O Edge(15, 100, 1005, mapOf("permission" to "na", "createdAt" to 15)),
                         */
                        listOf(
                            listOf("OUT", 15L, 100L, 1005L, 15L, "na", null),
                            listOf("OUT", 14L, 100L, 1004L, 14L, "me", null),
                        )
                    it.rows.size shouldBe 2
                }.verifyComplete()
        }

        "single condition on the index with multiple items: $singlePermissionIsMe" {
            val label = graph.getLabel(labelName)
            val scanFilter =
                ScanFilter(
                    name = labelName,
                    srcSet = setOf(src),
                    indexName = multipleItemIndex,
                    otherPredicates = singlePermissionIsMeCondition,
                )

            label
                .scan(scanFilter, emptySet())
                .test()
                .assertNext {
                    it.rows.map { row -> row.array.toList() } shouldBe
                        /**
                         * X Edge(10, 100, 1000, mapOf("permission" to "na", "createdAt" to 10)),
                         * X Edge(11, 100, 1001, mapOf("permission" to "others", "createdAt" to 11)),
                         * O Edge(12, 100, 1002, mapOf("permission" to "me", "createdAt" to 12)),
                         * X Edge(13, 100, 1003, mapOf("permission" to "others", "createdAt" to 13)),
                         * O Edge(14, 100, 1004, mapOf("permission" to "me", "createdAt" to 14)),
                         * X Edge(15, 100, 1005, mapOf("permission" to "na", "createdAt" to 15)),
                         */
                        listOf(
                            listOf("OUT", 14L, 100L, 1004L, 14L, "me", null),
                            listOf("OUT", 12L, 100L, 1002L, 12L, "me", null),
                        )
                    it.rows.size shouldBe 2
                }.verifyComplete()
        }

        "multiple conditions: $multiplePermissionIsMeAndCreatedAtIsGte" {
            val label = graph.getLabel(labelName)
            val scanFilter =
                ScanFilter(
                    name = labelName,
                    srcSet = setOf(src),
                    indexName = multipleItemIndex,
                    otherPredicates = multiplePermissionIsMeAndCreatedAtIsGteCondition,
                )

            label
                .scan(scanFilter, emptySet())
                .test()
                .assertNext {
                    it.rows.map { row -> row.array.toList() } shouldBe
                        /**
                         * X Edge(10, 100, 1000, mapOf("permission" to "na", "createdAt" to 10)),
                         * X Edge(11, 100, 1001, mapOf("permission" to "others", "createdAt" to 11)),
                         * X Edge(12, 100, 1002, mapOf("permission" to "me", "createdAt" to 12)),
                         * X Edge(13, 100, 1003, mapOf("permission" to "others", "createdAt" to 13)),
                         * O Edge(14, 100, 1004, mapOf("permission" to "me", "createdAt" to 14)),
                         * X Edge(15, 100, 1005, mapOf("permission" to "na", "createdAt" to 15)),
                         */
                        listOf(
                            listOf("OUT", 14L, 100L, 1004L, 14L, "me", null),
                        )
                    it.rows.size shouldBe 1
                }.verifyComplete()
        }
    })
