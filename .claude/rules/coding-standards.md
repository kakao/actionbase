# Coding Standards

## General Rules

- **File size**: Maximum 800 lines
- **Function size**: Maximum 50 lines
- **Nesting depth**: Maximum 4 levels
- **Immutability**: Prefer immutable data structures

## Kotlin Patterns

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

## Go Patterns

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

## Spring WebFlux

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

## Anti-Patterns to Avoid

- **God Object**: One class doing everything
- **Magic Numbers**: Use named constants
- **Deep Nesting**: Use early returns
- **Tight Coupling**: Use dependency injection
