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
 *    where column-by-column scannability matters. Each cell is the trimmed
 *    text between `|` separators; cells stay as `String` and are coerced to
 *    the test parameter's declared type (Int, Long, Boolean, Double, enum,
 *    String, ...) by Jackson at parameter-resolution time. The only special
 *    cell is `~`, which maps to `null` (so a literal `"~"` String cannot be
 *    expressed in pipe form — use a flow row instead). YAML strips `#`
 *    comments and blank lines between items naturally.
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
