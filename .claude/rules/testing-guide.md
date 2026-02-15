# Testing Guide

## Design Principle: Data-Driven, Review-Friendly Tests

Tests are written around **data, not code**.

- E2E tests use **raw JSON strings end-to-end** — request body in, response body out, visible in the diff.
- A reviewer should understand what is being tested **by reading `@ObjectSource` YAML data alone**, without tracing test code.
- Minimize builder, helper, and assertion abstractions. Input JSON and expected JSON must be directly visible.
- Test method bodies are thin wrappers around HTTP calls. Business logic is expressed by the data.

## Requirements

**ALL required for new features:**
1. **Unit Tests** - JUnit 5 / Go testing
2. **Integration Tests** - API endpoints, Storage
3. **CLI Tests** - Command execution

## TDD Workflow

1. Write test first (RED)
2. Run test - should FAIL
3. Write minimal implementation (GREEN)
4. Run test - should PASS
5. Refactor (IMPROVE)

## Kotlin Test Patterns

### Test Classification

| Type | Base Class | Assertions | Parameterization |
|------|-----------|------------|------------------|
| E2E (API) | `E2ETestBase()` | WebTestClient `.expectStatus()`, `.expectBody().json()` | `@ObjectSourceParameterizedTest` + `@ObjectSource` (YAML) |
| Unit | None | AssertJ `assertThat()`, `assertThatThrownBy()` | `@ObjectSourceParameterizedTest` + `@ObjectSource` (YAML) |

### Test File Structure

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureTest : E2ETestBase() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CreateDatabaseTest {
        @ObjectSourceParameterizedTest @ObjectSource(""" ... """)
        fun `create database`(name: String, create: String, expected: String) { ... }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CompatibilityTest {
        @BeforeAll
        fun setup() { /* create prerequisite resources */ }

        @ObjectSourceParameterizedTest @ObjectSource(""" ... """)
        fun `V2 create - V3 get`(name: String, create: String, expected: String) { ... }
    }

    @Nested
    inner class ValidationTest {
        @ParameterizedTest
        @ValueSource(strings = ["invalid!", "has space", "a".repeat(65)])
        fun `invalid names should fail`(name: String) { ... }
    }
}
```

**Key rules:**
- **One function, one feature.** Each test function verifies a single operation. The `@ObjectSource` data alone tells you what goes in and what comes out.
- Prefer independent tests with `@BeforeAll` for prerequisite setup.
- When state dependency is unavoidable (e.g. CRUD: update requires create), use `@TestMethodOrder(OrderAnnotation::class)` + `@Order(n)`. Keep ordered tests to a minimum — only where true sequential dependency exists.
- `@TestInstance(PER_CLASS)` on every class/nested class that uses `@BeforeAll`
- `@Nested` inner classes for logical grouping
- Backtick method names describing the operation

### E2E Tests (API)

Extend `E2ETestBase()` and use WebTestClient for HTTP assertions.

**Create (POST + GET verification):**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - name: db-basic
      create: |
        {"database": "db-basic", "comment": "test database"}
      expected: |
        {"database": "db-basic", "comment": "test database", "active": true}
    - name: db-empty
      create: |
        {"database": "db-empty", "comment": ""}
      expected: |
        {"database": "db-empty", "comment": "", "active": true}
    """,
)
fun `create database`(name: String, create: String, expected: String) {
    client.post().uri("/graph/v3/databases")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(create)
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expected)

    client.get().uri("/graph/v3/databases/$name")
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expected)
}
```

**Update (PUT + verify):**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - name: db-basic
      update: |
        {"comment": "updated comment"}
      expected: |
        {"database": "db-basic", "comment": "updated comment", "active": true}
    - name: db-empty
      update: |
        {"comment": "updated empty"}
      expected: |
        {"database": "db-empty", "comment": "updated empty", "active": true}
    """,
)
fun `update database`(name: String, update: String, expected: String) {
    client.put().uri("/graph/v3/databases/$name")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(update)
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expected)
}
```

**Cross-version compatibility:**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - name: db-v2v3-basic
      create: |
        {"desc": "test database"}
      expected: |
        {"database": "db-v2v3-basic", "comment": "test database", "active": true}
    """,
)
fun `V2 create - V3 get`(name: String, create: String, expected: String) {
    client.post().uri("/graph/v2/service/$name")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(create)
        .exchange()
        .expectStatus().isOk

    client.get().uri("/graph/v3/databases/$name")
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expected)
}
```

**@ObjectSource guidelines:**
- Use YAML `|` (literal block) for JSON payloads — avoids escaping
- Use YAML `#` comments to group related test cases
- Function parameter names must match YAML keys exactly
- Supported types: String, Int, Long, Boolean, List, Map (Jackson conversion)

**@ObjectSource parameters:**

```kotlin
@ObjectSource(
    value: String = "",   // test case data (default parameter)
    cases: String = "",   // alias for value — preferred when `shared` is present
    shared: String = "",  // shared fields merged into every test case
)
```

- `@ObjectSource("...")` — existing usage, backward compatible
- `@ObjectSource(cases = "...")` — alias for `value`, use when `shared` is also present for readability
- `@ObjectSource(shared = "...", cases = "...")` — `shared` fields are merged into every test case; per-case fields in `cases` override `shared` fields

**Shared fields with `shared`:**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    shared = """
      setup: |
        {"database": "test-db", "comment": "test"}
    """,
    cases = """
    - name: alias-basic
      update: |
        {"comment": "updated"}
    - name: alias-empty
      update: |
        {"comment": ""}
    """,
)
fun `update alias`(
    setup: String,     // from shared — shared across all cases
    name: String,      // from cases — per case
    update: String,    // from cases — per case
)
```

**Imports:**
```kotlin
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
```

### Unit Tests

No base class. Use AssertJ for assertions.

**Given/When/Then with inline objects:**

```kotlin
@Test
fun `ServiceEntity to DatabaseDescriptor`() {
    // Given
    val entity = ServiceEntity(name = "test-db", desc = "description")
    // When
    val result = converter.toDatabaseDescriptor(entity)
    // Then
    assertThat(result.database).isEqualTo("test-db")
    assertThat(result.comment).isEqualTo("description")
    assertThat(result.active).isTrue()
}
```

**Parameterized with @ObjectSource (enum/mapping tests):**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - v2: SYNC
      v3: SYNC
    - v2: ASYNC
      v3: ASYNC
    """,
)
fun `V2 to V3 MutationMode`(v2: String, v3: String) {
    val result = converter.toV3MutationMode(MutationMode.valueOf(v2))
    assertThat(result.name).isEqualTo(v3)
}
```

**Parameterized with @ObjectSource (validation tests):**

```kotlin
@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - name: valid-name
    - name: test_123
    - name: a
    """,
)
fun `valid names should pass validation`(name: String) {
    assertThat(V3NameValidator.validate(name)).isEqualTo(name)
}

@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - name: ""
    - name: "has space"
    - name: "dot.name"
    - name: "slash/name"
    """,
)
fun `invalid names should fail`(name: String) {
    assertThatThrownBy { V3NameValidator.validate(name) }
        .isInstanceOf(ResponseStatusException::class.java)
}
```

**Exception testing:**

```kotlin
@Test
fun `unknown type should throw`() {
    assertThatThrownBy { converter.convert(unknownInput) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("Unknown type")
}
```

### Parameterization Decision Guide

| Situation | Use |
|-----------|-----|
| Parameterized tests (all cases) | `@ObjectSourceParameterizedTest` + `@ObjectSource` (YAML) |
| Single scenario, no repetition | `@Test` |

**Always use `@ObjectSource`** for parameterized tests — even for simple enum mappings or string validation lists. This keeps the entire project consistent and reviewable in one format. Do NOT use `@CsvSource` or `@ValueSource`.

### Coverage Strategy

- **Positive path**: Independent per-feature verification (create, get, update, deactivate, delete)
- **Validation**: Invalid inputs, empty strings, special characters, max length
- **Cross-version**: V2 -> V3 and V3 -> V2 bidirectional compatibility
- **Boundary**: Edge cases (empty, max length, over-length)
- **Security**: Injection attempts (dot notation, path traversal)
- **Error codes**: 400 (bad request), 404 (not found), 409 (conflict)

## Go Test Example

```go
func TestProcess(t *testing.T) {
    tests := []struct{
        name  string
        input string
        want  string
    }{
        {"valid", "input", "output"},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            got := Process(tt.input)
            if got != tt.want {
                t.Errorf("Process(%q) = %q, want %q", tt.input, got, tt.want)
            }
        })
    }
}
```

## Code Quality Checklist

- Function >50 lines
- File >800 lines
- Nesting >4 levels
- Error handling
- Test coverage
