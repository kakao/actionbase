---
name: codemap-server
description: "Server module structure - REST API with Spring WebFlux, controllers, services, DTOs."
---

# Server Module

## Purpose

REST API using Spring WebFlux. Handles HTTP requests, validation, and response formatting.

## Package Structure

```
server/src/main/kotlin/.../
├── Application.kt            # Main entry point
├── controller/
│   ├── MutationController.kt # POST /api/v1/mutation
│   ├── QueryController.kt    # GET /api/v1/query
│   └── SchemaController.kt   # Schema management
├── service/
│   ├── MutationService.kt    # Business logic
│   └── QueryService.kt
├── dto/
│   ├── MutationRequest.kt
│   ├── MutationResponse.kt
│   ├── QueryRequest.kt
│   └── QueryResponse.kt
├── config/
│   ├── WebConfig.kt
│   └── SecurityConfig.kt
└── exception/
    └── GlobalExceptionHandler.kt
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/mutation` | Create/delete interaction |
| GET | `/api/v1/query` | Query interactions |
| GET | `/api/v1/schemas` | List schemas |
| GET | `/api/v1/schemas/{name}` | Get schema |

## Controller Pattern

```kotlin
@RestController
@RequestMapping("/api/v1")
class MutationController(
    private val mutationService: MutationService
) {
    @PostMapping("/mutation")
    fun create(@RequestBody request: MutationRequest): Mono<ApiResponse<MutationResult>> {
        return mutationService.process(request.toMutation())
            .map { ApiResponse.success(it) }
            .onErrorResume { ApiResponse.error(it) }
    }
}
```

## Reactive Rules

- Return `Mono<T>` or `Flux<T>` from all endpoints
- Never block in controller/service layer
- Use `subscribeOn(boundedElastic())` for blocking calls in engine

## Dependencies

- core (model)
- engine (storage/messaging)
- Spring WebFlux
