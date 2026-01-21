# Actionbase Claude Configuration

## Project Overview

Actionbase is a database for serving user interactions (likes, views, follows) at scale. It precomputes everything at write time so reads are fast and predictable.

**Core Concept**: _who_ did _what_ to which _target_

**Tech Stack**:
- **Backend**: Kotlin/Java with Spring WebFlux (Mono/Flux, not coroutines)
- **Storage**: Abstracted (currently HBase, SlateDB planned)
- **Metastore**: Abstracted (currently MySQL, consolidating into storage)
- **Messaging**: Abstracted (currently Kafka, file-based via SLF4J also supported)
- **Build**: Gradle (Kotlin/Java), Make (Go)

## Architecture

```
Backend:
  core/         Data model, mutation, query, encoding (Kotlin/Java)
  engine/       Storage and messaging bindings (Kotlin)
  server/       REST API with Spring WebFlux (Kotlin)
  (legacy: codec-java, core-java)

DX:
  cli/          Command-line client (Go + Cobra)
  guides/       Interactive demos (JavaScript)

Documentation:
  website/      Astro + Starlight
```

## Critical Rules

### Language Policy

- Conversation: Reply in the same language as the question
- Published content (code, comments, docs, PRs, issues): Always English
- Tone: Friendly, community-oriented

### Permission Policy

- Local changes: allowed (git commit, reset, rebase, build, test)
- Remote changes: explicit approval (git push, gh pr create, gh issue create)

### Code Organization

- Many small files over few large files
- High cohesion, low coupling
- 200-400 lines typical, 800 max per file
- Organize by feature/domain, not by type

### Code Style

- No emojis in code, comments, or documentation
- Immutability preferred - use data classes, val over var
- No println/System.out in production code (use SLF4J/Logback)
- No fmt.Print in Go production code (use proper logging)
- Input validation at API boundaries
- Run `./gradlew spotlessApply` before committing

### Kotlin/Java Specifics

```kotlin
// Data classes for immutability
data class Mutation(
    val schema: String,
    val userId: String,
    val targetId: String,
    val action: Action = Action.CREATE
)

// Sealed classes for results
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

// Reactive with Mono/Flux (NOT coroutines)
fun findById(id: String): Mono<Entity> = repository.findById(id)
fun findAll(): Flux<Entity> = repository.findAll()
```

### Go Specifics

```go
// Functional options pattern
type Option func(*Config)

func WithTimeout(d time.Duration) Option {
    return func(c *Config) { c.Timeout = d }
}

// Error handling
if err != nil {
    return fmt.Errorf("operation failed: %w", err)
}
```

## Key Patterns

### CQRS Architecture

```kotlin
// Mutation path
class MutationService(val engine: MutationEngine)
class MutationEngine(val storage: StorageClient, val messaging: MessagingClient)

// Query path
class QueryService(val engine: QueryEngine)
class QueryEngine(val storage: StorageClient)
```

### Reactive Non-Blocking

```kotlin
// GOOD: Non-blocking with Mono/Flux
return repository.findById(id).map { toResponse(it) }

// BAD: Blocking in reactive chain
val result = blockingOperation()  // BLOCKS event loop!
return Mono.just(result)

// Use boundedElastic for blocking calls
Mono.fromCallable { blockingStorageCall() }
    .subscribeOn(Schedulers.boundedElastic())
```

## Build Commands

```bash
# Kotlin/Java
./gradlew build                    # Full build
./gradlew test                     # Run tests
./gradlew spotlessApply            # Format code (run before commit)
./gradlew :server:bootRun          # Run server

# Go CLI
make build                         # Build CLI
make test                          # Run tests
go fmt ./...                       # Format code

# Docker
docker compose up -d               # Start local environment
./docker/standalone/build.sh       # Build standalone image
```

## Testing

### Principles

- TDD: Write tests first when possible
- Given/When/Then structure
- Use JUnit 5 `@ParameterizedTest` for same logic, different inputs

### Parameterized Test Pattern

```kotlin
@ParameterizedTest(name = "{0}. {1}")
@CsvSource(
    delimiter = '|',
    value = [
        // | # | description        | given  | when   | then   |
        "   1 | Empty state INSERT | false  | INSERT | true   ",
        "   2 | Active state UPDATE| true   | UPDATE | true   ",
        "   3 | Active state DELETE| true   | DELETE | false  ",
    ]
)
fun `test state transition`(
    index: Int,
    description: String,
    givenActive: Boolean,
    whenAction: String,
    thenActive: Boolean
) {
    // Given
    val state = createState(active = givenActive)

    // When
    val result = state.apply(whenAction)

    // Then
    assertThat(result.active).isEqualTo(thenActive)
}
```

### Commands

```bash
./gradlew test                     # Kotlin/Java tests
./gradlew :core:test               # Specific module
go test ./...                      # Go CLI tests
```

## Available Commands

- `/plan` - Create implementation plan for a feature
- `/build` - Run Gradle or Go build
- `/build-fix` - Fix build errors automatically
- `/tdd` - Test-driven development workflow
- `/test` - Run test suite
- `/code-review` - Review code for quality and security
- `/review` - Quick code review
- `/refactor-clean` - Clean up dead code
- `/update-docs` - Update documentation
- `/e2e` - Run end-to-end tests

## Git Workflow

### Conventional Commits

Format: `type(scope): description`

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

Scopes: `core`, `engine`, `server`, `cli`, `website`, `readme`, etc.

Examples:
```
feat(core): add bookmark schema support
fix(server): handle null userId in mutation
docs(readme): update quick start section
test(engine): add storage integration tests
```

### Before Push

Show commit tree, then immediately execute push (user confirms via permission prompt):
```bash
git log --graph --oneline --decorate -5
git push
```

### Templates

When creating PRs or issues, read the templates first and follow their format:
- PR: `.github/PULL_REQUEST_TEMPLATE.md`
- Issues: `.github/ISSUE_TEMPLATE/` (bug_report.md, feature_request.md, task.md)

## Security

- No hardcoded secrets (use environment variables)
- Validate all user inputs at API boundaries

## Project Links

- [Documentation](https://actionbase.io/) - `website/` deployed on GitHub Pages
- [GitHub](https://github.com/kakao/actionbase) - This codebase
- [Discussions](https://github.com/kakao/actionbase/discussions/)
