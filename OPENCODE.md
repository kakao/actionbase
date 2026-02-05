# Actionbase Worker Agent

You are a **Worker Agent** for developing [kakao/actionbase](https://github.com/kakao/actionbase) - a database for serving user interactions (likes, views, follows) at scale.

## Project Overview

**Core Concept**: **who** did **what** to which **target**

| Component | Technology | Purpose |
|-----------|------------|---------|
| Backend | Kotlin/Java | Spring WebFlux (reactive) |
| CLI | Go 1.21+ | Command-line interface |
| Build | Gradle 8+ | Kotlin DSL |
| Storage | HBase | Abstracted storage layer |
| Messaging | Kafka | CDC events |

## Architecture

```
         ┌──────────┐
         │   CLI    │ (Go)
         └────┬─────┘
              │ HTTP
              ▼
         ┌──────────┐
         │  Server  │ (Spring WebFlux)
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │  Engine  │ (Storage/Messaging bindings)
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │   Core   │ (Data model, encoding, validation)
         └──────────┘
```

## Build Commands

```bash
# Full build
./gradlew build
cd cli && go build ./...

# Test
./gradlew test
cd cli && go test ./...

# Specific module
./gradlew :core:build
./gradlew :server:build
```

## Coding Standards

### General Rules
- **File size**: Maximum 800 lines
- **Function size**: Maximum 50 lines
- **Nesting depth**: Maximum 4 levels
- **Immutability**: Prefer immutable data structures

### Kotlin Patterns

```kotlin
// Data class with defaults
data class Mutation(
    val schema: String,
    val userId: String,
    val targetId: String,
    val action: Action = Action.CREATE,
    val timestamp: Long = System.currentTimeMillis()
)

// Sealed class for results
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

// Null safety
fun process(user: User?) {
    user?.let { /* process */ }
}

// Extension functions
fun String.toRowKey(): ByteArray = this.toByteArray(Charsets.UTF_8)
```

### Go Patterns

```go
// Table-driven tests
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
            // test
        })
    }
}

// Error handling
result, err := doSomething()
if err != nil {
    return fmt.Errorf("failed: %w", err)
}

// Defer for cleanup
file, err := os.Open(filename)
if err != nil {
    return err
}
defer file.Close()
```

### Spring WebFlux

```kotlin
// Reactive return types
@GetMapping("/users/{id}")
fun getUser(@PathVariable id: String): Mono<User> {
    return userService.findById(id)
}

// Non-blocking with boundedElastic
Mono.fromCallable { blockingStorageCall() }
    .subscribeOn(Schedulers.boundedElastic())
```

## Testing Requirements

**ALL required for new features:**
1. **Unit Tests** - JUnit 5 / Go testing
2. **Integration Tests** - API endpoints, Storage
3. **CLI Tests** - Command execution

### TDD Workflow
1. Write test first (RED)
2. Run test - should FAIL
3. Write minimal implementation (GREEN)
4. Run test - should PASS
5. Refactor (IMPROVE)

```kotlin
@Test
fun `should process like mutation`() {
    // Given
    val mutation = Mutation(schema = "likes", userId = "user1", targetId = "post1")
    // When
    val result = processor.process(mutation)
    // Then
    assertTrue(result.isSuccess)
}
```

## Security Rules (CRITICAL)

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] Input sanitization for all API endpoints
- [ ] Error messages don't leak sensitive data

```kotlin
// NEVER
val apiKey = "sk-proj-xxxxx"

// ALWAYS
val apiKey = System.getenv("API_KEY")
    ?: throw IllegalStateException("API_KEY not configured")
```

```kotlin
// NEVER: Unvalidated user input in storage keys
val key = "$userId#$targetId"

// ALWAYS: Validate first
require(userId.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid userId" }
val key = "$userId#$targetId"
```

## Git Workflow

### Branch Naming
```
feature/add-bookmark-schema
fix/null-userid-validation
refactor/simplify-query-builder
```

### Commit Format
```
type(scope): description

feat(core): add bookmark schema support
fix(server): validate userId before processing
```

### Rules
- Never force push to main
- Require PR review before merge
- Ensure CI passes before merge
- Keep PRs focused and small

## Performance Guidelines

### Storage
```kotlin
// GOOD: Bounded scan
val scan = Scan().setPrefix(prefix).setLimit(100)

// BAD: Unbounded scan
val scan = Scan()  // Full table scan!
```

### Batch Operations
```kotlin
// GOOD: Batch writes
storage.putAll(listOfPuts)  // Single RPC

// BAD: Individual writes
puts.forEach { storage.put(it) }  // N RPCs
```

## Key Files

| Module | Entry Points |
|--------|-------------|
| core | `Mutation.kt`, `Query.kt`, `Schema.kt` |
| engine | `MutationEngine.kt`, `QueryEngine.kt` |
| server | `Application.kt`, `*Controller.kt` |
| cli | `main.go`, `cmd/*.go` |

## Code Review Checklist

### Security (CRITICAL)
- Hardcoded credentials
- Storage injection risks
- Input validation
- Authentication/authorization

### Code Quality (HIGH)
- Function >50 lines
- File >800 lines
- Nesting >4 levels
- Error handling
- Test coverage

### Performance (MEDIUM)
- Storage scan efficiency
- N+1 queries
- Blocking in reactive code

## Anti-Patterns to Avoid

- **God Object**: One class doing everything
- **Magic Numbers**: Use named constants
- **Deep Nesting**: Use early returns
- **Tight Coupling**: Use dependency injection

## Troubleshooting

### Build Fails
```bash
./gradlew build --stacktrace
cd cli && go build -v ./...
```

### Dependency Issues
```bash
./gradlew dependencies
./gradlew dependencyInsight --dependency <name>
cd cli && go mod tidy
```

## Remember

1. **Plan before coding** - Understand requirements
2. **Write tests first** - TDD for new features
3. **Review after coding** - Security and quality
4. **Commit frequently** - Small, focused commits
5. **Follow conventions** - Consistency matters
