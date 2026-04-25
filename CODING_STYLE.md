# Coding Style

This document captures the parts of Actionbase's coding style that are
settled. Other conventions exist in the codebase but have not yet been
formalized here; until they are, mirror the surrounding code.

## Dataflow as the program

Business logic in Actionbase is data being transformed: requests become
events, events get encoded, encoded data is written, and a response is
shaped from the result. Write this as a single pipeline so the flow reads
top-to-bottom in one place.

```kotlin
// GOOD: one chain, readable in one pass.
request
    .toEvents(schema)
    .writeWal(ctx)
    .groupBy { edge }
    .flatMap { mutateGroup(it) }
    .collectList()
    .map(::toResponse)
```

When two paths share most of their flow, push the differences into
parameters and keep the shared chain intact. A small amount of duplication
is acceptable if it preserves a clear, single-pass read.

```kotlin
// BAD: a generic pipeline runner with the real flow trapped in a lambda
// the caller passes in. The reader must jump caller → pipeline → lambda
// → caller — three hops to follow one flow.
executeMutationPipeline(
    events = eventFlux,
    executeGroup = { key, group ->
        sort(...) -> mutate(...) -> writeCDC(...) -> handleErr(...)
    },
).map { toResponse() }
```

This rule decides most code-review questions about structure: prefer the
shape that lets the next reader follow the flow without jumping.

## Spring WebFlux

The framework choice follows from the principle above. `Mono` and `Flux`
express a transformation pipeline in code that visibly matches the
dataflow; reactive operators compose into a chain that reads in one pass.
New code in Actionbase follows this style.

## Formatting

Enforced by Spotless + ktlint. Before submitting, run:

```bash
./gradlew spotlessApply
```

CI runs `spotlessCheck` and fails if formatting is not applied.
