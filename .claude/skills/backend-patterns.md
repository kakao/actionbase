---
name: backend-patterns
description: Backend architecture patterns, API design, HBase optimization, Kafka integration, and Spring WebFlux best practices for Actionbase.
---

# Backend Development Patterns

Backend architecture patterns and best practices for Actionbase.

## Architecture Overview

```
+----------------+     +----------------+     +----------------+
|   Clients      | --> |   Server       | --> |   Engine       |
| (REST/CLI)     |     | (Spring WebFlux)|    | (HBase/Kafka)  |
+----------------+     +----------------+     +----------------+
                              |
                       +------+------+
                       |             |
                  +--------+    +---------+
                  |  Core  |    | Metastore|
                  | (Model)|    | (MySQL)  |
                  +--------+    +---------+
```

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

// HBase implementation
class HBaseInteractionRepository(
    private val connection: Connection
) : InteractionRepository {

    override fun save(interaction: Interaction): Mono<Void> {
        return Mono.fromCallable {
            val table = connection.getTable(TableName.valueOf("interactions"))
            val put = Put(buildRowKey(interaction))
            put.addColumn(CF_DATA, COL_USER_ID, interaction.userId.toByteArray())
            put.addColumn(CF_DATA, COL_TARGET_ID, interaction.targetId.toByteArray())
            table.put(put)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun findByUserId(userId: String, limit: Int): Flux<Interaction> {
        return Flux.create<Interaction> { sink ->
            val table = connection.getTable(TableName.valueOf("interactions"))
            val scan = Scan()
                .setRowPrefixFilter(userId.toByteArray())
                .setLimit(limit)

            table.getScanner(scan).use { scanner ->
                for (result in scanner) {
                    sink.next(resultToInteraction(result))
                }
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
    private val kafkaProducer: KafkaProducer,
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
                kafkaProducer.send(savedMutation.toEvent())
                    .thenReturn(MutationResult(id = savedMutation.id, success = true))
            }
    }
}
```

## HBase Patterns

### Row Key Design (CRITICAL)

```kotlin
// GOOD: Composite row key for efficient scans
fun buildRowKey(schema: String, userId: String, timestamp: Long): ByteArray {
    // Format: schema#userId#reversedTimestamp
    // Reversed timestamp ensures newest first in scans
    val reversedTs = Long.MAX_VALUE - timestamp
    return "$schema#$userId#$reversedTs".toByteArray()
}

// GOOD: Avoid hotspotting with salted keys
fun buildSaltedRowKey(userId: String): ByteArray {
    val salt = userId.hashCode() % 10  // 0-9 prefix
    return "$salt#$userId".toByteArray()
}

// BAD: Timestamp first (creates hotspots)
fun badRowKey(timestamp: Long, userId: String): ByteArray {
    return "$timestamp#$userId".toByteArray()  // All writes go to same region
}
```

### Scan Optimization

```kotlin
// GOOD: Bounded scans with filters
fun queryInteractions(userId: String, limit: Int): List<Interaction> {
    val scan = Scan()
        .setRowPrefixFilter("likes#$userId#".toByteArray())
        .setLimit(limit)
        .setCaching(100)  // Prefetch rows
        .addFamily(CF_DATA)  // Only needed column family

    return table.getScanner(scan).use { scanner ->
        scanner.map { resultToInteraction(it) }
    }
}

// BAD: Full table scan
fun badQuery(): List<Interaction> {
    val scan = Scan()  // No filter - scans entire table!
    return table.getScanner(scan).use { scanner ->
        scanner.map { resultToInteraction(it) }
    }
}
```

### Batch Operations

```kotlin
// GOOD: Batch writes for efficiency
fun saveBatch(interactions: List<Interaction>) {
    val puts = interactions.map { interaction ->
        Put(buildRowKey(interaction)).apply {
            addColumn(CF_DATA, COL_USER_ID, interaction.userId.toByteArray())
            addColumn(CF_DATA, COL_TARGET_ID, interaction.targetId.toByteArray())
        }
    }
    table.put(puts)  // Single RPC for all puts
}

// BAD: Individual puts
fun badSaveBatch(interactions: List<Interaction>) {
    interactions.forEach { interaction ->
        table.put(buildPut(interaction))  // N RPCs
    }
}
```

## Kafka Patterns

### Producer Pattern

```kotlin
@Component
class InteractionEventProducer(
    private val kafkaTemplate: ReactiveKafkaProducerTemplate<String, InteractionEvent>
) {
    fun send(event: InteractionEvent): Mono<Void> {
        return kafkaTemplate.send(
            TOPIC_INTERACTIONS,
            event.userId,  // Key for partitioning
            event
        ).then()
    }

    fun sendBatch(events: List<InteractionEvent>): Flux<SenderResult<Void>> {
        return kafkaTemplate.send(
            Flux.fromIterable(events).map { event ->
                SenderRecord.create(
                    ProducerRecord(TOPIC_INTERACTIONS, event.userId, event),
                    null
                )
            }
        )
    }
}
```

### Consumer Pattern

```kotlin
@Component
class InteractionEventConsumer(
    private val processor: EventProcessor
) {
    @KafkaListener(
        topics = ["\${kafka.topic.interactions}"],
        groupId = "\${kafka.consumer.group-id}"
    )
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
    private val hbaseRepository: HBaseInteractionRepository,
    private val eventProducer: InteractionEventProducer
) : InteractionRepository by hbaseRepository {

    override fun save(interaction: Interaction): Mono<Void> {
        return hbaseRepository.save(interaction)
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
fun queryHBase(userId: String): Mono<List<Interaction>> {
    return Mono.fromCallable {
        // Blocking HBase call
        hbaseClient.query(userId)
    }.subscribeOn(Schedulers.boundedElastic())  // Run on blocking scheduler
}

// BAD: Blocking in reactive chain
fun badQuery(userId: String): Mono<List<Interaction>> {
    val result = hbaseClient.query(userId)  // BLOCKS event loop!
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
