# Design Patterns

See `CLAUDE.md` for code patterns (data class, sealed class, extension functions).
See `coding-style.md` for language-specific patterns.

## Architecture Patterns

### CQRS (Command Query Responsibility Segregation)

Actionbase uses separate paths for mutations and queries.

```kotlin
// Mutation path
class MutationService(val engine: MutationEngine)
class MutationEngine(val storage: StorageClient, val messaging: MessagingClient)

// Query path
class QueryService(val engine: QueryEngine)
class QueryEngine(val storage: StorageClient)
```

### Repository Pattern

Abstract data access behind interfaces.

```kotlin
interface InteractionRepository {
    fun save(interaction: Interaction): Mono<Void>
    fun findByUserId(userId: String): Flux<Interaction>
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

## Anti-Patterns to Avoid

- **God Object**: One class doing everything
- **Magic Numbers**: Use named constants
- **Deep Nesting**: Use early returns
- **Tight Coupling**: Use dependency injection
