# Design Patterns

## Architecture Patterns

### CQRS (Command Query Responsibility Segregation)
Actionbase uses separate paths for mutations and queries.

```kotlin
// Mutation path
class MutationService(val engine: MutationEngine)
class MutationEngine(val hbase: HBaseClient, val kafka: KafkaProducer)

// Query path
class QueryService(val engine: QueryEngine)
class QueryEngine(val hbase: HBaseClient)
```

### Repository Pattern
Abstract data access behind interfaces.

```kotlin
interface InteractionRepository {
    fun save(interaction: Interaction): Mono<Void>
    fun findByUserId(userId: String): Flux<Interaction>
}

class HBaseInteractionRepository : InteractionRepository {
    // HBase-specific implementation
}
```

### Service Layer Pattern
Business logic in service classes.

```kotlin
@Service
class MutationService(
    private val repository: InteractionRepository,
    private val validator: MutationValidator
) {
    fun process(mutation: Mutation): Mono<Result> {
        return validator.validate(mutation)
            .flatMap { repository.save(it.toInteraction()) }
    }
}
```

## Code Patterns

### Data Class (Kotlin)
```kotlin
data class Mutation(
    val schema: String,
    val userId: String,
    val targetId: String,
    val action: Action = Action.CREATE,
    val timestamp: Long = System.currentTimeMillis()
)
```

### Sealed Class for Results
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
```

### Extension Functions
```kotlin
fun String.toRowKey(): ByteArray = this.toByteArray(Charsets.UTF_8)
fun ByteArray.toRowKeyString(): String = String(this, Charsets.UTF_8)
```

## Go Patterns

### Functional Options
```go
type Option func(*Config)

func WithTimeout(d time.Duration) Option {
    return func(c *Config) { c.Timeout = d }
}

func NewClient(opts ...Option) *Client {
    config := defaultConfig()
    for _, opt := range opts {
        opt(config)
    }
    return &Client{config: config}
}
```

### Interface Composition
```go
type Reader interface { Read() error }
type Writer interface { Write() error }
type ReadWriter interface {
    Reader
    Writer
}
```

## Anti-Patterns to Avoid

- **God Object**: One class doing everything
- **Magic Numbers**: Use named constants
- **Deep Nesting**: Use early returns
- **Tight Coupling**: Use dependency injection
