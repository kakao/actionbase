---
name: backend-patterns
description: Backend architecture patterns, API design, storage optimization, messaging integration, and Spring WebFlux best practices for Actionbase.
---

# Backend Development Patterns

Backend architecture patterns and best practices for Actionbase.

## Architecture Overview

See CLAUDE.md for the full architecture diagram. Key layers:
- **Server**: Spring WebFlux REST API
- **Engine**: Storage and Messaging bindings
- **Core**: Data model and business logic
- **Metastore**: Schema metadata

## API Design Patterns

### RESTful API Structure

```kotlin
// Resource-based URLs
GET    /api/v1/schemas                 # List schemas
GET    /api/v1/schemas/:name           # Get schema
POST   /api/v1/mutation                # Create mutation
GET    /api/v1/query                   # Query interactions

// Query parameters for filtering
GET /api/v1/query?schema=likes&userId=user123&limit=100
```

### Spring WebFlux Controller

```kotlin
@RestController
@RequestMapping("/api/v1")
class MutationController(
    private val mutationService: MutationService
) {
    @PostMapping("/mutation")
    fun createMutation(@RequestBody request: MutationRequest): Mono<ResponseEntity<ApiResponse<MutationResult>>> {
        return mutationService.process(request.toMutation())
            .map { result ->
                ResponseEntity.ok(ApiResponse(success = true, data = result))
            }
            .onErrorResume { error ->
                Mono.just(
                    ResponseEntity.badRequest()
                        .body(ApiResponse(success = false, error = error.message))
                )
            }
    }

    @GetMapping("/query")
    fun query(
        @RequestParam schema: String,
        @RequestParam userId: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): Flux<Interaction> {
        return queryService.query(schema, userId, limit)
    }
}
```

### Repository Pattern

```kotlin
// Abstract data access
interface InteractionRepository {
    fun save(interaction: Interaction): Mono<Void>
    fun findByUserId(userId: String, limit: Int): Flux<Interaction>
    fun findByUserAndTarget(userId: String, targetId: String): Mono<Interaction?>
}

// Storage implementation (currently HBase)
class StorageInteractionRepository(
    private val storageClient: StorageClient
) : InteractionRepository {

    override fun save(interaction: Interaction): Mono<Void> {
        return Mono.fromCallable {
            storageClient.put(buildRowKey(interaction), interaction.toBytes())
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun findByUserId(userId: String, limit: Int): Flux<Interaction> {
        return Flux.create<Interaction> { sink ->
            storageClient.scan(userId, limit).forEach { result ->
                sink.next(resultToInteraction(result))
            }
            sink.complete()
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
```

### Service Layer Pattern

```kotlin
// Business logic separated from data access
@Service
class MutationService(
    private val repository: InteractionRepository,
    private val messagingProducer: MessagingProducer,
    private val validator: MutationValidator
) {
    fun process(mutation: Mutation): Mono<MutationResult> {
        return Mono.just(mutation)
            .flatMap { validator.validate(it) }
            .flatMap { validMutation ->
                repository.save(validMutation.toInteraction())
                    .thenReturn(validMutation)
            }
            .flatMap { savedMutation ->
                messagingProducer.send(savedMutation.toEvent())
                    .thenReturn(MutationResult(id = savedMutation.id, success = true))
            }
    }
}
```

## Storage Patterns

Row key encoding is finalized. See [Encoding Documentation](/internals/encoding/) for the format specification.

Key operational principles:
- Bounded scans with prefix filters and limits
- Batch operations for throughput
- Follow existing implementation patterns when modifying storage code

## Messaging Patterns

### Producer Pattern

```kotlin
@Component
class InteractionEventProducer(
    private val messagingTemplate: MessagingTemplate<String, InteractionEvent>
) {
    fun send(event: InteractionEvent): Mono<Void> {
        return messagingTemplate.send(
            TOPIC_INTERACTIONS,
            event.userId,  // Key for partitioning
            event
        ).then()
    }
}
```

### Consumer Pattern

```kotlin
@Component
class InteractionEventConsumer(
    private val processor: EventProcessor
) {
    fun consume(event: InteractionEvent, ack: Acknowledgment) {
        try {
            processor.process(event)
            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Failed to process event: ${event.id}", e)
            // Don't ack - will retry
        }
    }
}
```

### CDC (Change Data Capture) Pattern

```kotlin
// Emit events on data changes
class CDCEnabledRepository(
    private val storageRepository: StorageInteractionRepository,
    private val eventProducer: InteractionEventProducer
) : InteractionRepository by storageRepository {

    override fun save(interaction: Interaction): Mono<Void> {
        return storageRepository.save(interaction)
            .then(
                eventProducer.send(
                    InteractionEvent(
                        type = EventType.CREATED,
                        data = interaction
                    )
                )
            )
    }
}
```

## Spring WebFlux Patterns

### Reactive Error Handling

```kotlin
// Centralized error handler
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(SchemaNotFoundException::class)
    fun handleSchemaNotFound(ex: SchemaNotFoundException): ResponseEntity<ApiError> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError(code = "SCHEMA_NOT_FOUND", message = ex.message))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ApiError> {
        return ResponseEntity
            .badRequest()
            .body(ApiError(code = "VALIDATION_ERROR", message = ex.message))
    }
}

// In service layer
fun process(mutation: Mutation): Mono<Result> {
    return Mono.just(mutation)
        .filter { it.isValid() }
        .switchIfEmpty(Mono.error(ValidationException("Invalid mutation")))
        .flatMap { processValid(it) }
}
```

### Non-Blocking I/O (CRITICAL)

```kotlin
// GOOD: Non-blocking with subscribeOn
fun queryStorage(userId: String): Mono<List<Interaction>> {
    return Mono.fromCallable {
        // Blocking storage call
        storageClient.query(userId)
    }.subscribeOn(Schedulers.boundedElastic())  // Run on blocking scheduler
}

// BAD: Blocking in reactive chain
fun badQuery(userId: String): Mono<List<Interaction>> {
    val result = storageClient.query(userId)  // BLOCKS event loop!
    return Mono.just(result)
}
```

### Caching Pattern

```kotlin
@Service
class CachedSchemaService(
    private val schemaRepository: SchemaRepository
) {
    private val cache = ConcurrentHashMap<String, Schema>()

    fun findByName(name: String): Mono<Schema> {
        cache[name]?.let { return Mono.just(it) }

        return schemaRepository.findByName(name)
            .doOnNext { schema -> cache[name] = schema }
    }

    fun invalidate(name: String) {
        cache.remove(name)
    }
}
```

## Rate Limiting

```kotlin
@Component
class RateLimiter {
    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val windowStart = ConcurrentHashMap<String, Long>()

    fun checkLimit(key: String, maxRequests: Int, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val start = windowStart.computeIfAbsent(key) { now }

        if (now - start > windowMs) {
            // Reset window
            windowStart[key] = now
            requestCounts[key] = AtomicInteger(1)
            return true
        }

        val count = requestCounts.computeIfAbsent(key) { AtomicInteger(0) }
        return count.incrementAndGet() <= maxRequests
    }
}

// Usage in WebFilter
@Component
class RateLimitFilter(private val rateLimiter: RateLimiter) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val ip = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

        if (!rateLimiter.checkLimit(ip, maxRequests = 100, windowMs = 60_000)) {
            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            return exchange.response.setComplete()
        }

        return chain.filter(exchange)
    }
}
```

## Logging & Monitoring

```kotlin
// Structured logging
@Aspect
@Component
class LoggingAspect {

    @Around("@annotation(Logged)")
    fun logExecution(joinPoint: ProceedingJoinPoint): Any? {
        val start = System.currentTimeMillis()
        val methodName = joinPoint.signature.name

        return try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - start
            logger.info("method=$methodName status=success duration=${duration}ms")
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.error("method=$methodName status=error duration=${duration}ms error=${e.message}")
            throw e
        }
    }
}

// Usage
@Logged
fun processInteraction(interaction: Interaction): Result { }
```

**Remember**: Backend patterns enable scalable, maintainable server-side applications. Choose patterns that fit your complexity level.
