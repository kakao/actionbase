---
name: backend-patterns
description: Backend architecture patterns and Spring WebFlux best practices for Actionbase.
---

# Backend Development Patterns

See `CLAUDE.md` for architecture overview and reactive patterns.

## API Design

```kotlin
// Resource-based URLs
GET    /api/v1/schemas                 # List schemas
POST   /api/v1/mutation                # Create mutation
GET    /api/v1/query                   # Query interactions
```

## Core Patterns

### Repository Pattern

```kotlin
interface InteractionRepository {
    fun save(interaction: Interaction): Mono<Void>
    fun findByUserId(userId: String, limit: Int): Flux<Interaction>
}
```

### Service Layer

```kotlin
@Service
class MutationService(
    private val repository: InteractionRepository,
    private val messaging: MessagingProducer
) {
    fun process(mutation: Mutation): Mono<MutationResult> {
        return repository.save(mutation.toInteraction())
            .then(messaging.send(mutation.toEvent()))
            .thenReturn(MutationResult(success = true))
    }
}
```

## Storage Patterns

Row key encoding is finalized. See [Encoding Documentation](/internals/encoding/).

Principles:
- Bounded scans with prefix filters
- Batch operations for throughput
- Use `subscribeOn(boundedElastic())` for blocking calls

## Messaging Patterns

```kotlin
// Producer
fun send(event: Event): Mono<Void> {
    return messagingTemplate.send(TOPIC, event.key, event).then()
}

// Consumer with acknowledgment
fun consume(event: Event, ack: Acknowledgment) {
    processor.process(event)
    ack.acknowledge()
}
```

## WebFlux Essentials

### Non-Blocking I/O (CRITICAL)

```kotlin
// GOOD
Mono.fromCallable { blockingCall() }
    .subscribeOn(Schedulers.boundedElastic())

// BAD - blocks event loop
val result = blockingCall()
Mono.just(result)
```

### Error Handling

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ValidationException::class)
    fun handle(ex: ValidationException) = ResponseEntity.badRequest()
        .body(ApiError(code = "VALIDATION_ERROR", message = ex.message))
}
```

## Anti-Patterns

- Blocking in reactive chain
- Unbounded scans
- Individual writes instead of batch
- N+1 queries
