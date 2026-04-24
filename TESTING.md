# Testing

Actionbase is codifying its test conventions gradually. At this stage we
commit to one rule; more patterns will be added later.

## Data-Driven Test (`@ObjectSource`)

If your test is a table of input and expected output — input on the left,
expected on the right — use `@ObjectSource` with YAML. Each case is *data*,
not code: intent is readable from the YAML alone, and adding a case is a
data edit. Do not use `@CsvSource` or `@ValueSource`.

Good fits: parsers, validators, mappers, serializers — anywhere the test
body reduces to "compute `actual` from `input`, assert against `expected`".

```kotlin
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

@ObjectSourceParameterizedTest
@ObjectSource(
    """
    - uri: datastore://my_namespace/my_table
      namespace: my_namespace
      table: my_table
    - uri: datastore:///my_table
      namespace: ""
      table: my_table
    - uri: datastore://ns/t
      namespace: ns
      table: t
    """,
)
fun `valid URI`(uri: String, namespace: String, table: String) {
    val (ns, tbl) = DatastoreUri.parse(uri)
    assertEquals(namespace, ns)
    assertEquals(table, tbl)
}
```

- YAML fields map to method parameters by name (Jackson binds them). Each
  case becomes its own JUnit invocation, so a failure points at one offending
  case.
- Infrastructure lives in `core/src/testFixtures/kotlin/com/kakao/actionbase/test/documentations/params/`.
- Escape hatch: `@ParameterizedTest` + `@MethodSource` — only when cases must
  be generated at runtime.
- See `DatastoreUriTest`, `V3NameValidatorTest`, `ObjectSourceTest` for more
  patterns (shared fields, nested cases, JSON block scalars).

## Other tests

Write whatever is clearest for the case at hand.

## Naming and assertions

- Class: `XxxTest`.
- Method: backtick sentence — `` `should do X when Y` ``.
- Kotlin: `kotlin.test.*`. Java: `org.junit.jupiter.api.Assertions.*`.

## Running

```bash
./gradlew test
./gradlew :engine:test --tests '*MutationServiceTest'
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the broader workflow.
