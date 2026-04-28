package com.kakao.actionbase.test.state

import com.kakao.actionbase.test.allCombinations
import com.kakao.actionbase.test.allCombinationsWithRepetition
import com.kakao.actionbase.test.allPermutationsWithRepetition
import com.kakao.actionbase.test.cartesianProduct
import com.kakao.actionbase.test.combinations
import com.kakao.actionbase.test.combinationsWithRepetition
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource
import com.kakao.actionbase.test.parseAsList
import com.kakao.actionbase.test.permutations
import com.kakao.actionbase.test.permutationsWithRepetition

import kotlin.streams.toList

import org.junit.jupiter.api.Assertions.assertEquals

class CombinatoricsTest {
    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  given   | expected
        - "[]      | [[]]"
        - "[1]     | [[1]]"
        - "[1,2]   | [[1,2],[2,1]]"
        - "[A,B,C] | [[A,B,C],[A,C,B],[B,A,C],[B,C,A],[C,A,B],[C,B,A]]"
        """,
    )
    fun `test permutations - basic permutations (order matters, no duplicates)`(
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.permutations().toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  r  | given        | expected
        - "0  | [1, 2, 3]    | [[]]"
        - "1  | [1, 2, 3]    | [[1], [2], [3]]"
        - "2  | [1, 2, 3]    | [[1, 2], [1, 3], [2, 3]]"
        - "3  | [1, 2, 3]    | [[1, 2, 3]]"
        - "2  | [A, B, C, D] | [[A, B], [A, C], [A, D], [B, C], [B, D], [C, D]]"
        - "-1 | [1, 2, 3]    | []"
        - "4  | [1, 2, 3]    | []"
        """,
    )
    fun `test combinations - basic combinations (order doesn't matter, no duplicates)`(
        r: Int,
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.combinations(r).toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  given     | expected
        - "[]        | [[]]"
        - "[A]       | [[], [A]]"
        - "[A, B]    | [[], [A], [B], [A, B]]"
        - "[1, 2, 3] | [[], [1], [2], [3], [1, 2], [1, 3], [2, 3], [1, 2, 3]]"
        """,
    )
    fun `test allCombinations - all size combinations (from empty set to full set)`(
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.allCombinations().toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  length | given     | expected
        - "0      | [A, B]    | [[]]"
        - "1      | [A, B]    | [[A], [B]]"
        - "2      | [A, B]    | [[A, A], [A, B], [B, A], [B, B]]"
        - "1      | [1, 2, 3] | [[1], [2], [3]]"
        - "2      | [X, Y]    | [[X, X], [X, Y], [Y, X], [Y, Y]]"
        - "3      | [I, U]    | [[I, I, I], [I, I, U], [I, U, I], [I, U, U], [U, I, I], [U, I, U], [U, U, I], [U, U, U]]"
        """,
    )
    fun `test permutationsWithRepetition - permutations with repetition (order matters, repetition allowed)`(
        length: Int,
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.permutationsWithRepetition(length).toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  r  | given     | expected
        - "0  | [A, B]    | [[]]"
        - "1  | [A, B]    | [[A], [B]]"
        - "2  | [A, B]    | [[A, A], [A, B], [B, B]]"
        - "1  | [1, 2, 3] | [[1], [2], [3]]"
        - "2  | [1, 2, 3] | [[1, 1], [1, 2], [1, 3], [2, 2], [2, 3], [3, 3]]"
        - "3  | [X, Y]    | [[X, X, X], [X, X, Y], [X, Y, Y], [Y, Y, Y]]"
        - "-1 | [A, B]    | []"
        """,
    )
    fun `test combinationsWithRepetition - combinations with repetition (order doesn't matter, repetition allowed)`(
        r: Int,
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.combinationsWithRepetition(r).toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  maxLength | given     | expected
        - "1         | [A, B]    | [[A], [B]]"
        - "2         | [A, B]    | [[A], [B], [A, A], [A, B], [B, A], [B, B]]"
        - "1         | [X, Y, Z] | [[X], [Y], [Z]]"
        - "2         | [1, 2]    | [[1], [2], [1, 1], [1, 2], [2, 1], [2, 2]]"
        - "3         | [I, U]    | [[I], [U], [I, I], [I, U], [U, I], [U, U], [I, I, I], [I, I, U], [I, U, I], [I, U, U], [U, I, I], [U, I, U], [U, U, I], [U, U, U]]"
        - "3         | [I, U, D] | [[I], [U], [D], [I, I], [I, U], [I, D], [U, I], [U, U], [U, D], [D, I], [D, U], [D, D], [I, I, I], [I, I, U], [I, I, D], [I, U, I], [I, U, U], [I, U, D], [I, D, I], [I, D, U], [I, D, D], [U, I, I], [U, I, U], [U, I, D], [U, U, I], [U, U, U], [U, U, D], [U, D, I], [U, D, U], [U, D, D], [D, I, I], [D, I, U], [D, I, D], [D, U, I], [D, U, U], [D, U, D], [D, D, I], [D, D, U], [D, D, D]]"
        """,
    )
    fun `test allPermutationsWithRepetition - all length permutations with repetition (from 1 to maxLength)`(
        maxLength: Int,
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.allPermutationsWithRepetition(maxLength).toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  maxLength | given     | expected
        - "1         | [A, B]    | [[A], [B]]"
        - "2         | [A, B]    | [[A], [B], [A, A], [A, B], [B, B]]"
        - "1         | [X, Y, Z] | [[X], [Y], [Z]]"
        - "2         | [1, 2]    | [[1], [2], [1, 1], [1, 2], [2, 2]]"
        - "3         | [I, U]    | [[I], [U], [I, I], [I, U], [U, U], [I, I, I], [I, I, U], [I, U, U], [U, U, U]]"
        """,
    )
    fun `test allCombinationsWithRepetition - all length combinations with repetition (from 1 to maxLength)`(
        maxLength: Int,
        givenNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = givenNotation.parseAsList()

        // when
        val actual = given.allCombinationsWithRepetition(maxLength).toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #  lists                    | expected
        - "[]                       | [[]]"
        - "[[A]]                    | [[A]]"
        - "[[1], [A]]               | [[1, A]]"
        - "[[A, B], [1, 2]]         | [[A, 1], [A, 2], [B, 1], [B, 2]]"
        - "[[X, Y], [1, 2, 3]]      | [[X, 1], [X, 2], [X, 3], [Y, 1], [Y, 2], [Y, 3]]"
        - "[[A, B], [X, Y], [1, 2]] | [[A, X, 1], [A, X, 2], [A, Y, 1], [A, Y, 2], [B, X, 1], [B, X, 2], [B, Y, 1], [B, Y, 2]]"
        """,
    )
    fun `test cartesianProduct - cartesian product (all combinations of multiple lists)`(
        listsNotation: String,
        expectedNotation: String,
    ) {
        // given
        val given = listsNotation.parseAsList() as List<List<Any>>

        // when
        val actual = given.cartesianProduct().toList()

        // then
        val expected = expectedNotation.parseAsList()
        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }
}
