---
description: Test-Driven Development workflow - write tests first, then implement.
---

# TDD Command

Follow Test-Driven Development methodology:

## Workflow

### 1. RED - Write Failing Test
```kotlin
// Kotlin/JUnit 5
@Test
fun `should process like mutation`() {
    val mutation = LikeMutation(userId = "user1", targetId = "post1")
    val result = processor.process(mutation)
    assertTrue(result.isSuccess)
}
```

```go
// Go
func TestProcessMutation(t *testing.T) {
    mutation := &Mutation{UserID: "user1", TargetID: "post1"}
    result, err := processor.Process(mutation)
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
}
```

### 2. Verify Test Fails
```bash
./gradlew test  # Should fail
cd cli && go test ./...  # Should fail
```

### 3. GREEN - Write Minimal Implementation
Just enough code to make the test pass.

### 4. Verify Test Passes
```bash
./gradlew test  # Should pass
cd cli && go test ./...  # Should pass
```

### 5. REFACTOR - Improve Code
- Remove duplication
- Improve naming
- Optimize (if needed)
- Keep tests green

### 6. Check Coverage
```bash
./gradlew jacocoTestReport
cd cli && go test -cover ./...
```

## Test Types

1. **Unit Tests** - Individual functions/classes
2. **Integration Tests** - API endpoints, HBase queries
3. **CLI Tests** - Command execution

## Commands

```bash
# Run all tests
./gradlew test
cd cli && go test ./...

# Run specific module
./gradlew :core:test
./gradlew :server:test

# With coverage
./gradlew test jacocoTestReport
```

## Remember

- Write test FIRST
- One test at a time
- Keep tests independent
- Test edge cases
