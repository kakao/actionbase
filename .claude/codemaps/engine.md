# Engine Module

## Purpose

Storage and Messaging bindings. Connects core model to external systems.

## Package Structure

```
engine/src/main/kotlin/.../
├── storage/
│   ├── StorageClient.kt      # Abstract storage interface
│   ├── HBaseStorageClient.kt # HBase implementation
│   └── StorageConfig.kt
├── messaging/
│   ├── MessagingProducer.kt  # Abstract producer
│   ├── KafkaProducer.kt      # Kafka implementation
│   └── MessagingConfig.kt
├── mutation/
│   └── MutationEngine.kt     # Mutation processing
└── query/
    └── QueryEngine.kt        # Query processing
```

## Key Classes

### MutationEngine
```kotlin
class MutationEngine(
    private val storageClient: StorageClient,
    private val messagingProducer: MessagingProducer
) {
    fun process(mutation: Mutation): Mono<MutationResult>
}
```

### QueryEngine
```kotlin
class QueryEngine(
    private val storageClient: StorageClient
) {
    fun query(query: Query): Flux<Interaction>
}
```

## Storage Abstraction

| Method | Description |
|--------|-------------|
| `put(key, value)` | Write single row |
| `putBatch(rows)` | Batch write |
| `get(key)` | Get single row |
| `scan(prefix, limit)` | Prefix scan |

## Messaging Abstraction

| Method | Description |
|--------|-------------|
| `send(topic, key, event)` | Send event |
| `sendBatch(events)` | Batch send |

## Non-Blocking (CRITICAL)

All storage/messaging calls must use `Schedulers.boundedElastic()`:

```kotlin
Mono.fromCallable { storageClient.put(key, value) }
    .subscribeOn(Schedulers.boundedElastic())
```

## Dependencies

- core (model)
- HBase client
- Kafka client
- Used by: server
