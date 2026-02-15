---
name: codemap-core
description: "Core module structure - data model definitions, encoding logic, validation rules."
---

# Core Module

## Purpose

Data model definitions, encoding logic, and validation rules.
No external dependencies (pure Kotlin/Java).

## Package Structure

```
core/src/main/kotlin/.../
├── model/
│   ├── Mutation.kt       # Mutation data class
│   ├── Query.kt          # Query data class
│   ├── Schema.kt         # Schema definition
│   └── Interaction.kt    # Stored interaction
├── encoding/
│   ├── RowKeyBuilder.kt  # Row key construction (FIXED)
│   └── ValueEncoder.kt   # Value serialization
├── validation/
│   └── MutationValidator.kt
└── result/
    └── Result.kt         # Sealed class for results
```

## Key Classes

### Mutation
```kotlin
data class Mutation(
    val schema: String,
    val userId: String,
    val targetId: String,
    val action: Action = Action.CREATE,
    val timestamp: Long = System.currentTimeMillis()
)
```

### Query
```kotlin
data class Query(
    val schema: String,
    val userId: String,
    val limit: Int = 100,
    val cursor: String? = null
)
```

### Result Pattern
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
```

## Encoding

Row key format is finalized. See `/internals/encoding/` documentation.

Do not modify encoding logic without careful review.

## Dependencies

- None (pure domain model)
- Used by: engine, server
