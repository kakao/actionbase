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

class AllFiltersSpec :
    StringSpec({

        lateinit var graph: Graph

        val labelName = EntityName(serviceName, GraphFixtures.hbaseIndexed)
        val src = "100"

        // ---------------------------------------------------------------------
        //  Test‑data helpers ( mirrors the edges inserted by GraphFixtures )
        // ---------------------------------------------------------------------
        data class EdgeMeta(
            val createdAt: Long,
            val permission: String,
        )

        /**
         * Edges are stored in *descending* `createdAt` order by the *_desc* indices
         * we are exercising below.
         */
        val edges: List<EdgeMeta> =
            listOf(
                EdgeMeta(15, "na"),
                EdgeMeta(14, "me"),
                EdgeMeta(13, "others"),
                EdgeMeta(12, "me"),
                EdgeMeta(11, "others"),
                EdgeMeta(10, "na"),
            )

        val singleItemIndex = "created_at_desc" // index on createdAt only
        val multiItemIndex = "permission_created_at_desc" // composite (permission, createdAt)

        // --------------------------------------------------------------------
        //  Generic predicate helpers ----------------------------------------
        // --------------------------------------------------------------------
        fun createdAtMatches(
            value: Long,
            op: String,
            bound: Long,
            upper: Long? = null,
        ): Boolean =
            when (op) {
                "eq" -> value == bound
                "gt" -> value > bound
                "gte" -> value >= bound
                "lt" -> value < bound
                "lte" -> value <= bound
                "bt" -> value in bound..(upper ?: bound)
                else -> false
            }

        fun expectedCreatedAts(
            op: String,
            bound: Long,
            upper: Long? = null,
            permission: String? = null,
        ): List<Long> =
            edges
                .filter { edge -> (permission == null || edge.permission == permission) && createdAtMatches(edge.createdAt, op, bound, upper) }
                .map { it.createdAt }

        // --------------------------------------------------------------------
        //  Lifecycle --------------------------------------------------------
        // --------------------------------------------------------------------
        beforeTest { graph = GraphFixtures.create() }
        afterTest { graph.close() }

        // --------------------------------------------------------------------
        //  Parsing – one assertion per generated filter spec ----------------
        // --------------------------------------------------------------------
        "parsing: createdAt and permission predicates" {
            // single‑bound operators
            listOf("eq", "gt", "gte", "lt", "lte").forEach { op ->
                val spec = "createdAt:$op:14"
                val parsed = WherePredicate.parse(spec).single()
                val expected =
                    when (op) {
                        "eq" -> WherePredicate.Eq("createdAt", "14")
                        "gt" -> WherePredicate.Gt("createdAt", "14")
                        "gte" -> WherePredicate.Gte("createdAt", "14")
                        "lt" -> WherePredicate.Lt("createdAt", "14")
                        "lte" -> WherePredicate.Lte("createdAt", "14")
                        else -> error("impossible")
                    }
                parsed shouldBe expected
            }
            // bt
            val btParsed = WherePredicate.parse("createdAt:bt:12,14").single()
            btParsed shouldBe WherePredicate.Between("createdAt", "12", "14")

            // permission = me / others
            listOf("me", "others").forEach { perm ->
                WherePredicate.parse("permission:eq:$perm").single() shouldBe WherePredicate.Eq("permission", perm)
            }
        }

        // --------------------------------------------------------------------
        //  Single createdAt conditions (index with a single item) -----------
        // --------------------------------------------------------------------
        listOf("eq", "gt", "gte", "lt", "lte").forEach { op ->
            listOf(10L, 11L, 12L, 13L, 14L, 15L).forEach { createdAtBound ->
                "single condition on created_at_desc: createdAt:$op:$createdAtBound" {
                    val predicate = WherePredicate.parse("createdAt:$op:$createdAtBound").toSet()
                    val label = graph.getLabel(labelName)
                    val expected = expectedCreatedAts(op, createdAtBound)

                    label
                        .scan(
                            ScanFilter(
                                name = labelName,
                                srcSet = setOf(src),
                                indexName = singleItemIndex,
                                otherPredicates = predicate,
                            ),
                            emptySet(),
                        ).test()
                        .assertNext {
                            it.rows.map { row -> row.array[1] as Long } shouldBe expected
                            it.rows.size shouldBe expected.size
                        }.verifyComplete()
                }
            }
        }

        // bt
        "single condition on created_at_desc: createdAt:bt:12,14" {
            val predicate = WherePredicate.parse("createdAt:bt:12,14").toSet()
            val label = graph.getLabel(labelName)
            val expected = expectedCreatedAts("bt", 12, 14)

            label
                .scan(
                    ScanFilter(
                        name = labelName,
                        srcSet = setOf(src),
                        indexName = singleItemIndex,
                        otherPredicates = predicate,
                    ),
                    emptySet(),
                ).test()
                .assertNext {
                    it.rows.map { row -> row.array[1] as Long } shouldBe expected
                    it.rows.size shouldBe expected.size
                }.verifyComplete()
        }

        // --------------------------------------------------------------------
        //  Single permission conditions (index with multiple items) ---------
        // --------------------------------------------------------------------
        listOf("me", "others").forEach { perm ->
            "single condition on permission_created_at_desc: permission:eq:$perm" {
                val predicate = WherePredicate.parse("permission:eq:$perm").toSet()
                val expected = expectedCreatedAts("lte", Long.MAX_VALUE, permission = perm) // all rows for that permission
                val label = graph.getLabel(labelName)

                label
                    .scan(
                        ScanFilter(
                            name = labelName,
                            srcSet = setOf(src),
                            indexName = multiItemIndex,
                            otherPredicates = predicate,
                        ),
                        emptySet(),
                    ).test()
                    .assertNext {
                        it.rows.map { row -> row.array[1] as Long } shouldBe expected
                        it.rows.size shouldBe expected.size
                    }.verifyComplete()
            }
        }

        // --------------------------------------------------------------------
        //  Combined conditions ---------------------------------------------
        // --------------------------------------------------------------------
        val createdAtOps = listOf("eq", "gt", "gte", "lt", "lte")
        listOf("me", "others").forEach { perm ->
            createdAtOps.forEach { op ->
                listOf(10L, 11L, 12L, 13L, 14L, 15L).forEach { createdAtBound ->
                    val spec = "permission:eq:$perm;createdAt:$op:$createdAtBound"
                    "combined condition ($spec)" {
                        val predicates = WherePredicate.parse(spec).toSet()
                        val expected = expectedCreatedAts(op, createdAtBound, permission = perm)
                        val label = graph.getLabel(labelName)

                        label
                            .scan(
                                ScanFilter(
                                    name = labelName,
                                    srcSet = setOf(src),
                                    indexName = multiItemIndex,
                                    otherPredicates = predicates,
                                ),
                                emptySet(),
                            ).test()
                            .assertNext {
                                it.rows.map { row -> row.array[1] as Long } shouldBe expected
                                it.rows.size shouldBe expected.size
                            }.verifyComplete()
                    }
                }
            }
            // bt
            val btSpec = "permission:eq:$perm;createdAt:bt:12,14"
            "combined condition ($btSpec)" {
                val predicates = WherePredicate.parse(btSpec).toSet()
                val expected = expectedCreatedAts("bt", 12, 14, permission = perm)
                val label = graph.getLabel(labelName)

                label
                    .scan(
                        ScanFilter(
                            name = labelName,
                            srcSet = setOf(src),
                            indexName = multiItemIndex,
                            otherPredicates = predicates,
                        ),
                        emptySet(),
                    ).test()
                    .assertNext {
                        it.rows.map { row -> row.array[1] as Long } shouldBe expected
                        it.rows.size shouldBe expected.size
                    }.verifyComplete()
            }
        }
    })
