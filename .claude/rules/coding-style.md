# Coding Style Guidelines

## General Rules

- **File size**: Maximum 800 lines per file
- **Function size**: Maximum 50 lines per function
- **Nesting depth**: Maximum 4 levels
- **Immutability**: Prefer immutable data structures

## Kotlin Guidelines

```kotlin
// GOOD: Immutable data class
data class User(val id: String, val name: String)

// GOOD: Null safety
fun process(user: User?): String = user?.name ?: "Unknown"

// GOOD: Extension functions
fun String.toRowKey(): ByteArray = this.toByteArray()

// GOOD: Early returns
if (input == null) return
if (!isValid(input)) return
process(input)
```

## Go Guidelines

```go
// GOOD: Error handling
result, err := doSomething()
if err != nil {
    return fmt.Errorf("failed: %w", err)
}

// GOOD: Table-driven tests
func TestProcess(t *testing.T) {
    tests := []struct{
        name string
        input string
        want string
    }{
        {"valid", "input", "output"},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // test
        })
    }
}
```

## Naming Conventions

- **Kotlin/Java**: `camelCase` for variables, `PascalCase` for classes
- **Go**: `camelCase` for private, `PascalCase` for public
- **Files**: `PascalCase.kt` for Kotlin, `snake_case.go` for Go

## Logging

```kotlin
// Use proper logging, not println
logger.info("Processing mutation: {}", mutation.id)
logger.error("Failed to process", exception)
```

```go
// Use proper logging
log.Printf("Processing mutation: %s", mutation.ID)
log.Fatalf("Failed to process: %v", err)
```

## Comments

- Explain WHY, not WHAT
- Document public APIs with KDoc/GoDoc
- Remove commented-out code (use git history)
