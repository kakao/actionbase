package com.kakao.actionbase.test.documentations.params

/**
 * Dense-matrix data source for `@ObjectSourceParameterizedTest`.
 *
 * The annotation value is a YAML list. Each item is one row; cells map to
 * the test method's parameters by position. Use this when a test is a table
 * of primitives where repeating YAML keys per case would hurt readability.
 *
 * A row item can be either shape:
 *
 * 1. **Flow list** `[a, b, c]` — concise for short, simple tables.
 *
 *    ```
 *    @TableSource("""
 *        - [IDLE,    START, RUNNING]
 *        - [RUNNING, STOP,  IDLE]
 *    """)
 *    fun `transition`(from: State, event: Event, expected: State) { ... }
 *    ```
 *
 * 2. **Pipe-delimited string** `a | b | c` — recommended for wide matrices
 *    where column-by-column scannability matters. Each cell is parsed as a
 *    YAML scalar (so `~` is null, bare numbers are typed, single-quoted
 *    strings keep their content). YAML strips `#` comments and blank lines
 *    between items naturally.
 *
 *    ```
 *    @TableSource("""
 *        # state machine transitions
 *        - IDLE    | START | RUNNING
 *        - RUNNING | STOP  | IDLE
 *    """)
 *    fun `transition`(from: State, event: Event, expected: State) { ... }
 *    ```
 *
 * For nested or heterogeneous data, use `@ObjectSource` instead.
 */
@Retention
@Target(AnnotationTarget.FUNCTION)
annotation class TableSource(
    val value: String,
)
