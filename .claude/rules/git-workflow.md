# Git Workflow

## Branch Naming

```
feature/add-bookmark-schema
fix/null-userid-validation
refactor/simplify-query-builder
```

## Commit Format

```
type(scope): description

feat(core): add bookmark schema support
fix(server): validate userId before processing
refactor(engine): simplify query builder logic
test(core): add mutation processing tests
docs(readme): update build instructions
```

## Rules

- Never force push to main
- Require PR review before merge
- Ensure CI passes before merge
- Keep PRs focused and small
- Commit frequently with clear messages

## Performance Guidelines

### Storage
```kotlin
// GOOD: Bounded scan
val scan = Scan().setPrefix(prefix).setLimit(100)

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

### Reactive Code
- Never block in reactive pipeline
- Use `Schedulers.boundedElastic()` for blocking calls
- Avoid N+1 queries
