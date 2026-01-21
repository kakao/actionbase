# Performance Guidelines

## HBase Performance

### Row Key Design
```kotlin
// GOOD: Efficient scan pattern
// userId first for user-centric queries
val rowKey = "$schema#$userId#$reversedTimestamp"

// BAD: Timestamp first causes hotspots
val rowKey = "$timestamp#$userId"  // All writes to same region!
```

### Scan Optimization
```kotlin
// GOOD: Bounded scan
val scan = Scan()
    .setRowPrefixFilter(prefix)
    .setLimit(100)
    .setCaching(100)  // Prefetch

// BAD: Unbounded scan
val scan = Scan()  // Full table scan!
```

### Batch Operations
```kotlin
// GOOD: Batch writes
table.put(listOfPuts)  // Single RPC

// BAD: Individual writes
puts.forEach { table.put(it) }  // N RPCs
```

## Kafka Performance

### Producer
- Use async sends for throughput
- Batch messages when possible
- Consider compression

### Consumer
- Process in batches
- Use appropriate commit strategy
- Monitor lag

## Spring WebFlux

### Non-Blocking I/O
```kotlin
// GOOD: Non-blocking
return repository.findById(id)
    .map { toResponse(it) }

// BAD: Blocking in reactive chain
val result = blockingOperation()  // BLOCKS event loop!
return Mono.just(result)
```

### Use boundedElastic for blocking
```kotlin
Mono.fromCallable { blockingHBaseCall() }
    .subscribeOn(Schedulers.boundedElastic())
```

## General Rules

- Avoid N+1 queries
- Use pagination (cursor-based)
- Cache frequently accessed data
- Profile before optimizing
- Set reasonable timeouts
