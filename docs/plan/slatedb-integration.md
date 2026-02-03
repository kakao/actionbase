# SlateDB Integration Plan

## Overview

Integrate [SlateDB](https://github.com/slatedb/slatedb) as an alternative storage backend for Actionbase using Java 25 FFI (Foreign Function & Memory API, JEP 454).

## Goals

- Minimal FFI wrapper (no unnecessary complexity)
- Use slatedb-c directly (no custom Rust wrapper)
- Match existing HBaseTable interface pattern
- Non-blocking reactive integration with Mono/Flux

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Actionbase                          │
├─────────────────────────────────────────────────────────┤
│  QueryEngine / MutationEngine                           │
├──────────────────┬──────────────────┬───────────────────┤
│   HBaseStorage   │   SlateDbStorage │   LocalStorage    │
├──────────────────┼──────────────────┼───────────────────┤
│   HBase Client   │  SlateDbNative   │   In-Memory       │
│                  │  (Java 25 FFI)   │                   │
├──────────────────┼──────────────────┼───────────────────┤
│   HBase Server   │  libslatedb_c    │        -          │
│                  │  (Rust/C)        │                   │
└──────────────────┴──────────────────┴───────────────────┘
```

## Implementation Steps

### Phase 1: FFI Foundation (Current)

- [x] Build script for slatedb-c (`native/build-slatedb.sh`)
- [x] Update .gitignore for native artifacts
- [x] SlateDbNative.kt - minimal FFI wrapper
- [x] Basic unit tests
- [ ] Verify FFI bindings work correctly

### Phase 2: Storage Integration

- [ ] SlateDbOptions.kt - configuration
- [ ] SlateDbStorage.kt - implements Storage<SlateDbOptions>
- [ ] SlateDbTable.kt - reactive wrapper (Mono/Flux)
- [ ] Integration tests

### Phase 3: Engine Integration

- [ ] Register SlateDB as storage type
- [ ] Configuration support (application.yml)
- [ ] End-to-end tests with QueryEngine/MutationEngine

### Phase 4: Production Readiness

- [ ] Error handling and recovery
- [ ] Metrics and monitoring
- [ ] Performance benchmarks
- [ ] Documentation

## File Structure

```
native/
├── build-slatedb.sh          # Build script (clones & builds slatedb-c)
├── slatedb/                   # Cloned repo (gitignore)
└── lib/
    └── libslatedb_c.dylib    # Built library (gitignore)

engine/src/main/kotlin/.../storage/slatedb/
├── SlateDbNative.kt          # FFI bindings
├── SlateDbTable.kt           # Reactive wrapper
├── SlateDbStorage.kt         # Storage implementation
└── SlateDbOptions.kt         # Configuration
```

## API Mapping

| Operation | HBase | SlateDB FFI |
|-----------|-------|-------------|
| get | `get(Get): Mono<Result>` | `slatedb_get_with_options` |
| put | `put(Put): Mono<Void>` | `slatedb_put_with_options` |
| delete | `delete(Delete): Mono<Void>` | `slatedb_delete_with_options` |
| scan | `scan(Scan, limit): Mono<List<Result>>` | `slatedb_scan_prefix_with_options` |

## Prerequisites

- Java 25 (for FFI support)
- Rust nightly (slatedb uses unstable features)
- rustup (for managing Rust toolchains)

## Build Instructions

```bash
# Install rustup if not present
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Build slatedb-c
./native/build-slatedb.sh

# Run tests
./gradlew :engine:test --tests "*.SlateDbNativeTest"
```

## References

- [SlateDB](https://github.com/slatedb/slatedb)
- [slatedb-c](https://github.com/slatedb/slatedb/tree/main/slatedb-c)
- [JEP 454: Foreign Function & Memory API](https://openjdk.org/jeps/454)
