# Actionbase Architecture

## Overview

Actionbase is a database for serving user interactions (likes, views, follows) at scale.
Precomputes everything at write time for fast, predictable reads.

## Core Concept

**who** did **what** to which **target**

## Module Dependency

```
         ┌──────────┐
         │   CLI    │ (Go)
         └────┬─────┘
              │ HTTP
              ▼
         ┌──────────┐
         │  Server  │ (Spring WebFlux)
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │  Engine  │ (Storage/Messaging bindings)
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │   Core   │ (Data model, encoding, validation)
         └──────────┘
```

## Data Flow

### Mutation (Write)
```
Client → Server → Engine → Storage
                       ↘→ Messaging (CDC)
```

### Query (Read)
```
Client → Server → Engine → Storage → Response
```

## Key Files

| Module | Entry Point |
|--------|-------------|
| core | `core/src/main/.../Mutation.kt`, `Query.kt`, `Schema.kt` |
| engine | `engine/src/main/.../MutationEngine.kt`, `QueryEngine.kt` |
| server | `server/src/main/.../Application.kt` |
| cli | `cli/main.go` |

## External Dependencies

| Component | Current | Abstraction |
|-----------|---------|-------------|
| Data Store | HBase | Storage |
| Event Stream | Kafka | Messaging |
| Metadata | MySQL | Metastore |

## Build

- **Kotlin/Java**: Gradle 8+ (Kotlin DSL)
- **Go CLI**: Make + Go modules
- **Docs**: Astro + Starlight
