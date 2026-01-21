# Performance Guidelines

## Storage Performance

### Key Design
```kotlin
// GOOD: Efficient scan pattern
// userId first for user-centric queries
val key = "$schema#$userId#$reversedTimestamp"

// BAD: Timestamp first causes hotspots
val key = "$timestamp#$userId"  // All writes to same region!
```

### Scan Optimization
```kotlin
// GOOD: Bounded scan
val scan = Scan()
    .setPrefix(prefix)
    .setLimit(100)

// BAD: Unbounded scan
val scan = Scan()  // Full table scan!
```

### Batch Operations
```kotlin
// GOOD: Batch writes
storage.putAll(listOfPuts)  // Single RPC

// BAD: Individual writes
puts.forEach { storage.put(it) }  // N RPCs
```

## Messaging Performance

### Producer
- Use async sends for throughput
- Batch messages when possible
- Consider compression

### Consumer
- Process in batches
- Use appropriate commit strategy
- Monitor lag

## Spring WebFlux

See `CLAUDE.md` for reactive patterns.

```kotlin
// Use boundedElastic for blocking calls
Mono.fromCallable { blockingStorageCall() }
    .subscribeOn(Schedulers.boundedElastic())
```

## General Rules

- Avoid N+1 queries
- Use pagination (cursor-based)
- Cache frequently accessed data
- Profile before optimizing
- Set reasonable timeouts
