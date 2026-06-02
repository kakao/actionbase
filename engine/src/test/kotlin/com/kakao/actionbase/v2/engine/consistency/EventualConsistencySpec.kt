package com.kakao.actionbase.v2.engine.consistency

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.util.getLogger

import kotlin.random.Random

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Event sequences in this test represent possible (valid) state transitions.
 *
 * Possible event sequence rules:
 * - Must start with I (insert/upsert)
 * - After Insert: can receive I (upsert), U (update), or D (delete)
 * - After Update: can receive I (upsert), U (update), or D (delete)
 * - After Delete: can only receive I (insert/upsert)
 *
 * Event sequence: the order in which events occurred (event time order)
 * Processing sequence: the order in which events are processed by the system
 *
 * allValidEventSequences: all possible event sequences for a given depth
 * uniqueSubsequences: all possible subsequences of event sequences for testing partial processing
 * processingOrders: all possible processing orders of the same event sequence
 */
class EventualConsistencySpec :
    StringSpec({

        // use ENVIRONMENT variable MAX_DEPTH to set the depth
        val maxDepth = System.getenv("MAX_DEPTH")?.toIntOrNull() ?: 1

        val labelName = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed)

        lateinit var graph: Graph
        lateinit var label: HBaseIndexedLabel

        beforeTest {
            graph = GraphFixtures.create()
            val fetchedLabel = graph.getLabel(labelName)
            require(fetchedLabel is HBaseIndexedLabel)
            label = fetchedLabel
        }

        afterTest {
            graph.close()
        }

        val allValidEventSequences = generateAllValidEventSequences(maxDepth)
        logger.info("The allValidEventSequences for test are ${allValidEventSequences.size}")

        val uniqueSubsequences = generateUniqueSubsequences(allValidEventSequences)
        logger.info("Unique subsequences: ${uniqueSubsequences.size}")

        uniqueSubsequences.forEachIndexed { i, eventSequence ->
            val progress = 100.0 * (i + 1) / uniqueSubsequences.size
            val progressStr = "%.0f%%".format(progress)

            val testName = "Test sequence [${eventSequence.name()}] (${i + 1}) "

            "[$progressStr] src fixed $testName" {
                val base = newTestSetup()
                val tests =
                    listOf(
                        base.create(
                            src = ConstantIdGenerator(9999990L),
                            tgt = SequenceIdGenerator(1000000L),
                            useLegacy = true,
                        ),
                        base.create(
                            src = ConstantIdGenerator(9999991L),
                            tgt = SequenceIdGenerator(1000000L),
                            useLegacy = false,
                        ),
                    )
                tests.forEach {
                    testAnyProcessingOrder(
                        graph,
                        label,
                        eventSequence,
                        it.base,
                        it.src,
                        it.tgt,
                    )
                }

                val getQueryResult =
                    tests.map {
                        label.get(it.src.base, it.tgt.base, Direction.OUT, setOf(StatKey.WITH_ALL))
                    }

                DataFrameStepVerifier
                    .create(getQueryResult)
                    .assertFieldsEqual("active", "ts", "createdAt", "permission")
                    .verifyComplete()

                val scanQueryResult =
                    tests.map {
                        val scanFilter =
                            ScanFilter(
                                name = label.name,
                                srcSet = setOf(it.src.base),
                                dir = Direction.OUT,
                                indexName = "created_at_desc",
                            )
                        label.scan(scanFilter, emptySet())
                    }

                DataFrameStepVerifier
                    .create(scanQueryResult)
                    .assertFieldsEqual("ts", "createdAt", "permission")
                    .verifyComplete()
            }

            "[$progressStr] tgt fixed $testName" {
                val base = newTestSetup()
                val tests =
                    listOf(
                        base.create(
                            src = SequenceIdGenerator(1000000L),
                            tgt = ConstantIdGenerator(9999990L),
                            useLegacy = true,
                        ),
                        base.create(
                            src = SequenceIdGenerator(1000000L),
                            tgt = ConstantIdGenerator(9999991L),
                            useLegacy = false,
                        ),
                    )

                tests.forEach {
                    testAnyProcessingOrder(
                        graph,
                        label,
                        // if (it.useLegacy) label.useLegacyIndex() else label.useV2Index(),
                        eventSequence,
                        it.base,
                        it.src,
                        it.tgt,
                    )
                }

                val getQueryResult =
                    tests.map {
                        label.get(it.src.base, it.tgt.base, Direction.OUT, setOf(StatKey.WITH_ALL))
                    }

                DataFrameStepVerifier
                    .create(getQueryResult)
                    .assertFieldsEqual("active", "ts", "createdAt", "permission")
                    .verifyComplete()

                val scanQueryResult =
                    tests.map {
                        val scanFilter =
                            ScanFilter(
                                name = label.name,
                                srcSet = setOf(it.tgt.base),
                                dir = Direction.IN,
                                indexName = "created_at_desc",
                            )
                        label.scan(scanFilter, emptySet())
                    }

                DataFrameStepVerifier
                    .create(scanQueryResult)
                    .assertFieldsEqual("ts", "createdAt", "permission")
                    .verifyComplete()
            }
        }
    }) {
    interface IdGenerator {
        val base: Long

        fun next(): Long
    }

    class SequenceIdGenerator(
        start: Long,
    ) : IdGenerator {
        override val base = start
        private var id = start

        override fun next(): Long = id++
    }

    class ConstantIdGenerator(
        private val value: Long,
    ) : IdGenerator {
        override val base = value

        override fun next(): Long = value
    }

    companion object {
        val logger = getLogger()

        fun generateAllValidEventSequences(n: Int): List<EventSequence> {
            /**
             * def generate_sequences(current_sequence, depth, last_op):
             *     if depth == 0:
             *         return [current_sequence]
             *
             *     sequences = []
             *     next_ops = ['I', 'U', 'D'] if last_op != 'D' else ['I']
             *     for op in next_ops:
             *         sequences.extend(generate_sequences(current_sequence + op, depth - 1, op))
             *
             *     return sequences
             *
             * def main():
             *     depth = 7
             *     sequences = generate_sequences('I', depth - 1, 'I')
             *
             *     # Print sequences with '/' delimiter, 5 per line
             *     for i in range(0, len(sequences), 5):
             *         print('/'.join(sequences[i:i+5]))
             *
             * if __name__ == '__main__':
             *     main()
             *
             */
            val allValidRawOpSequences =
                """
                IIIIIII/IIIIIIU/IIIIIID/IIIIIUI/IIIIIUU/IIIIIUD/IIIIIDI/IIIIUII/IIIIUIU/IIIIUID
                IIIIUUI/IIIIUUU/IIIIUUD/IIIIUDI/IIIIDII/IIIIDIU/IIIIDID/IIIUIII/IIIUIIU/IIIUIID
                IIIUIUI/IIIUIUU/IIIUIUD/IIIUIDI/IIIUUII/IIIUUIU/IIIUUID/IIIUUUI/IIIUUUU/IIIUUUD
                IIIUUDI/IIIUDII/IIIUDIU/IIIUDID/IIIDIII/IIIDIIU/IIIDIID/IIIDIUI/IIIDIUU/IIIDIUD
                IIIDIDI/IIUIIII/IIUIIIU/IIUIIID/IIUIIUI/IIUIIUU/IIUIIUD/IIUIIDI/IIUIUII/IIUIUIU
                IIUIUID/IIUIUUI/IIUIUUU/IIUIUUD/IIUIUDI/IIUIDII/IIUIDIU/IIUIDID/IIUUIII/IIUUIIU
                IIUUIID/IIUUIUI/IIUUIUU/IIUUIUD/IIUUIDI/IIUUUII/IIUUUIU/IIUUUID/IIUUUUI/IIUUUUU
                IIUUUUD/IIUUUDI/IIUUDII/IIUUDIU/IIUUDID/IIUDIII/IIUDIIU/IIUDIID/IIUDIUI/IIUDIUU
                IIUDIUD/IIUDIDI/IIDIIII/IIDIIIU/IIDIIID/IIDIIUI/IIDIIUU/IIDIIUD/IIDIIDI/IIDIUII
                IIDIUIU/IIDIUID/IIDIUUI/IIDIUUU/IIDIUUD/IIDIUDI/IIDIDII/IIDIDIU/IIDIDID/IUIIIII
                IUIIIIU/IUIIIID/IUIIIUI/IUIIIUU/IUIIIUD/IUIIIDI/IUIIUII/IUIIUIU/IUIIUID/IUIIUUI
                IUIIUUU/IUIIUUD/IUIIUDI/IUIIDII/IUIIDIU/IUIIDID/IUIUIII/IUIUIIU/IUIUIID/IUIUIUI
                IUIUIUU/IUIUIUD/IUIUIDI/IUIUUII/IUIUUIU/IUIUUID/IUIUUUI/IUIUUUU/IUIUUUD/IUIUUDI
                IUIUDII/IUIUDIU/IUIUDID/IUIDIII/IUIDIIU/IUIDIID/IUIDIUI/IUIDIUU/IUIDIUD/IUIDIDI
                IUUIIII/IUUIIIU/IUUIIID/IUUIIUI/IUUIIUU/IUUIIUD/IUUIIDI/IUUIUII/IUUIUIU/IUUIUID
                IUUIUUI/IUUIUUU/IUUIUUD/IUUIUDI/IUUIDII/IUUIDIU/IUUIDID/IUUUIII/IUUUIIU/IUUUIID
                IUUUIUI/IUUUIUU/IUUUIUD/IUUUIDI/IUUUUII/IUUUUIU/IUUUUID/IUUUUUI/IUUUUUU/IUUUUUD
                IUUUUDI/IUUUDII/IUUUDIU/IUUUDID/IUUDIII/IUUDIIU/IUUDIID/IUUDIUI/IUUDIUU/IUUDIUD
                IUUDIDI/IUDIIII/IUDIIIU/IUDIIID/IUDIIUI/IUDIIUU/IUDIIUD/IUDIIDI/IUDIUII/IUDIUIU
                IUDIUID/IUDIUUI/IUDIUUU/IUDIUUD/IUDIUDI/IUDIDII/IUDIDIU/IUDIDID/IDIIIII/IDIIIIU
                IDIIIID/IDIIIUI/IDIIIUU/IDIIIUD/IDIIIDI/IDIIUII/IDIIUIU/IDIIUID/IDIIUUI/IDIIUUU
                IDIIUUD/IDIIUDI/IDIIDII/IDIIDIU/IDIIDID/IDIUIII/IDIUIIU/IDIUIID/IDIUIUI/IDIUIUU
                IDIUIUD/IDIUIDI/IDIUUII/IDIUUIU/IDIUUID/IDIUUUI/IDIUUUU/IDIUUUD/IDIUUDI/IDIUDII
                IDIUDIU/IDIUDID/IDIDIII/IDIDIIU/IDIDIID/IDIDIUI/IDIDIUU/IDIDIUD/IDIDIDI
                """.trimIndent()
                    .split(Regex("[/\n]"))

            // Example processing with n=3:
            // Input sequences: ["IUI", "IUDIU", "IUDID"]
            // 1. take(n)   -> ["IUI", "IUD", "IUD"]     // Take first n=3 characters
            // 2. distinct  -> ["IUI", "IUD"]            // Remove duplicates
            // 3. sorted   -> ["IUD", "IUI"]            // Sort for consistent ordering
            val rawOpSequences =
                allValidRawOpSequences
                    .map { it.take(n) }
                    .distinct()
                    .sorted()

            // Convert character sequences to OpAndEventTime objects
            // Example for sequence "IUD":
            // [(0, I), (1, U), (2, D)] ->
            // [
            //   OpAndEventTime(INSERT, 0),
            //   OpAndEventTime(UPDATE, 1),
            //   OpAndEventTime(DELETE, 2)
            // ]
            return rawOpSequences
                .map { rawOpSequence ->
                    val sequence =
                        rawOpSequence.withIndex().map { (index, op) ->
                            val ts = index.toLong() // using index as event time
                            when (op) {
                                'I' -> OpAndEventTime(EdgeOperation.INSERT, ts)
                                'U' -> OpAndEventTime(EdgeOperation.UPDATE, ts)
                                'D' -> OpAndEventTime(EdgeOperation.DELETE, ts)
                                else -> throw IllegalArgumentException("Invalid operation: $op")
                            }
                        }
                    EventSequence(sequence)
                }
        }

        fun generateUniqueSubsequences(eventSequences: List<EventSequence>): List<EventSequence> {
            /**
             *  Generates all possible subsequences (combinations) from the input list.
             *  Used to generate subsets of event sequences to test partial event processing.
             *
             *  Example:
             *  Input: [I, U, D]
             *  Output: [
             *    [],        // empty set
             *    [I],       // single elements
             *    [U],
             *    [D],
             *    [I, U],    // pairs
             *    [I, D],
             *    [U, D],
             *    [I, U, D]  // full set
             *  ]
             */
            fun <T> generateSubEventSequenceList(list: List<T>): List<List<T>> {
                // combination
                if (list.isEmpty()) return listOf(listOf())
                val element = list.first()
                val rest = generateSubEventSequenceList(list.drop(1))
                return rest + rest.map { it + element }
            }

            return eventSequences
                .flatMap { eventSequence ->
                    generateSubEventSequenceList(eventSequence.items).filter { it.isNotEmpty() }
                }.distinct()
                .sortedWith(
                    compareBy<List<OpAndEventTime>> {
                        it.size
                    }.thenComparator { a, b ->
                        val aString =
                            a.joinToString {
                                it.op.name
                                    .first()
                                    .toString()
                            }
                        val bString =
                            b.joinToString {
                                it.op.name
                                    .first()
                                    .toString()
                            }
                        aString.compareTo(bString)
                    },
                ).map {
                    EventSequence(it)
                }
        }

        @Suppress("LongMethod")
        private fun testAnyProcessingOrder(
            graph: Graph,
            label: HBaseHashLabel,
            eventSequence: EventSequence,
            testSetup: BaseTestSetup,
            srcGenerator: IdGenerator,
            tgtGenerator: IdGenerator,
        ) {
            /**
             * Generates all possible orderings (permutations) of the input list.
             * Used to test different processing orders of the same events for eventual consistency.
             *
             * Example:
             * Input: [I, U, D]
             * Output: [
             *   [I, U, D],
             *   [I, D, U],
             *   [U, I, D],
             *   [U, D, I],
             *   [D, I, U],
             *   [D, U, I]
             * ]
             */
            fun <T> getAllPossibleProcessingOrders(list: List<T>): List<List<T>> {
                if (list.size <= 1) return listOf(list)
                val result = mutableListOf<List<T>>()
                for (i in list.indices) {
                    val element = list[i]
                    val remaining = list.toMutableList().apply { removeAt(i) }
                    for (perm in getAllPossibleProcessingOrders(remaining)) {
                        result.add(listOf(element) + perm)
                    }
                }
                return result
            }

            val possibleProcessingOrders = getAllPossibleProcessingOrders(eventSequence.items)

            val results =
                possibleProcessingOrders.map { processingSequence ->
                    val request =
                        toRequest(
                            processingSequence,
                            testSetup,
                            srcGenerator,
                            tgtGenerator,
                        )

                    var acc = 0L
                    val cdcContext =
                        request.items
                            .map { (op, edge) ->
                                val cdc = label.mutate(edge, op).block()
                                acc += cdc!!.acc
                                cdc
                            }.last()

                    val edge = request.items.first().edge
                    val src = edge.src
                    val tgt = edge.tgt
                    val row =
                        graph
                            .queryGet(label.entity.name, src, tgt, setOf(StatKey.WITH_ALL))
                            .toRowFlux()
                            .blockFirst()

                    val rawBytes = label.getRawHashEdgeValueForTest(src, tgt).block()

                    rawBytes.shouldNotBeNull()

                    listOf(
                        cdcContext.label,
                        cdcContext.alias,
                        cdcContext.after?.props,
                        acc,
                        rawBytes,
                        row?.getOrNull("active"),
                        row?.getOrNull("ts"),
                        row?.getOrNull("createdAt"),
                        row?.getOrNull("permission"),
                    )
                }

            // Check that all results are the same
            val reference = results.first()
            results.drop(1).forEach { other ->
                reference.zip(other).forEach { (a, b) ->
                    a.shouldBe(b)
                }
            }
        }

        fun toRequest(
            processingSequence: List<OpAndEventTime>,
            testSetup: BaseTestSetup,
            srcGenerator: IdGenerator,
            tgtGenerator: IdGenerator,
        ): MutationRequest {
            val src = srcGenerator.next()
            val tgt = tgtGenerator.next()
            val request =
                processingSequence
                    .map { (op, ts) ->
                        val edge =
                            when (op) {
                                EdgeOperation.INSERT ->
                                    Edge(
                                        ts,
                                        src,
                                        tgt,
                                        mapOf(
                                            "createdAt" to ts,
                                            "permission" to testSetup.insertPermission,
                                            "receivedFrom" to testSetup.insertReceivedFrom,
                                        ),
                                    )
                                EdgeOperation.UPDATE ->
                                    Edge(
                                        ts,
                                        src,
                                        tgt,
                                        mapOf(
                                            "permission" to testSetup.updatePermission,
                                            "receivedFrom" to testSetup.updateReceivedFrom,
                                        ).filterValues { it != null },
                                    )
                                EdgeOperation.DELETE ->
                                    Edge(
                                        ts,
                                        src,
                                        tgt,
                                        // this props value is ignored.
                                        mapOf(
                                            "createdAt" to Random.nextLong(),
                                            "permission" to Random.nextLong().toString(),
                                            "receivedFrom" to Random.nextLong().toString(),
                                        ),
                                    )
                                else -> throw IllegalArgumentException("Invalid operation: $op")
                            }
                        TraceEdgeMutation(op, edge.toTraceEdge())
                    }
            return MutationRequest(request)
        }

        fun newTestSetup() =
            BaseTestSetup(
                listOf("not_received", "others", null).random(),
                listOf("not_received", "others", null).random(),
                listOf("me", "others", null).random(),
                listOf("me", "others").random(),
            )
    }
}
