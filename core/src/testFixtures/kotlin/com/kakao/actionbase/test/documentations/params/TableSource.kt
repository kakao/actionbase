package com.kakao.actionbase.test.documentations.params

/**
 * Dense-matrix data source for `@ObjectSourceParameterizedTest`.
 *
 * Body is YAML with two top-level keys — `columns` (list of parameter names)
 * and `rows` (list of value lists). Each row is expanded into a test case
 * whose keys come from `columns`. Use this when a test is a table of
 * primitives where repeating YAML keys per case would hurt readability.
 *
 * For nested or heterogeneous data, use `@ObjectSource` instead.
 *
 * Example:
 * ```
 * @ObjectSourceParameterizedTest
 * @TableSource("""
 *     columns: [from, event, expected]
 *     rows:
 *       - [IDLE,    START, RUNNING]
 *       - [RUNNING, STOP,  IDLE]
 * """)
 * fun `transition moves state`(from: State, event: Event, expected: State) { ... }
 * ```
 */
@Retention
@Target(AnnotationTarget.FUNCTION)
annotation class TableSource(
    val value: String,
)
