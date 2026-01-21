# Actionbase Claude Configuration

## Project Overview

Actionbase is a database for serving user interactions (likes, views, follows) at scale. It precomputes everything at write time so reads are fast and predictable.

**Core Concept**: _who_ did _what_ to which _target_

**Tech Stack**:
- **Backend**: Kotlin/Java with Spring WebFlux (reactive)
- **CLI**: Go with Cobra
- **Storage**: HBase for data, MySQL for metastore
- **Messaging**: Kafka for WAL/CDC
- **Build**: Gradle (Kotlin/Java), Make (Go)
- **Docs**: Astro + Starlight

## Architecture

```
core/       Data model, mutation, query, encoding (Java/Kotlin)
engine/     HBase and Kafka bindings (Kotlin)
server/     REST API with Spring WebFlux (Kotlin)
cli/        Command-line client (Go)
website/    Documentation site (Astro/Starlight)
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

// Non-blocking with Spring WebFlux
fun findById(id: String): Mono<Entity> = repository.findById(id)
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
class MutationEngine(val hbase: HBaseClient, val kafka: KafkaProducer)

// Query path
class QueryService(val engine: QueryEngine)
class QueryEngine(val hbase: HBaseClient)
```

### HBase Row Key Design

```kotlin
// GOOD: userId first for user-centric queries
val rowKey = "$schema#$userId#$reversedTimestamp"

// BAD: Timestamp first causes hotspots
val rowKey = "$timestamp#$userId"  // All writes to same region!
```

### Reactive Non-Blocking

```kotlin
// GOOD: Non-blocking
return repository.findById(id).map { toResponse(it) }

// BAD: Blocking in reactive chain
val result = blockingOperation()  // BLOCKS event loop!
return Mono.just(result)

// Use boundedElastic for blocking calls
Mono.fromCallable { blockingHBaseCall() }
    .subscribeOn(Schedulers.boundedElastic())
```

## Build Commands

```bash
# Kotlin/Java
./gradlew build                    # Full build
./gradlew test                     # Run tests
./gradlew spotlessApply            # Format code
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

- TDD: Write tests first when possible
- 80% minimum coverage target
- Unit tests for utilities and domain logic
- Integration tests with TestContainers for HBase/Kafka
- Use `./gradlew test` for Kotlin/Java, `go test ./...` for Go

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

- Conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `perf:`
- Main branch: `main`
- PRs require review before merge
- All tests must pass before merge

### Before Push

Always show commit tree before pushing for confirmation:
```bash
git log origin/<branch>..HEAD --oneline
git diff origin/<branch> --stat
```

### Templates

When creating PRs or issues, read the templates first and follow their format:
- PR: `.github/PULL_REQUEST_TEMPLATE.md`
- Issues: `.github/ISSUE_TEMPLATE/` (bug_report.md, feature_request.md, task.md)

## Security

- No hardcoded secrets (use environment variables)
- Validate all user inputs at API boundaries
- HBase ACLs for data access control
- Kafka ACLs for topic access
- HTTPS for production traffic

## Environment Variables

```bash
# HBase
HBASE_ZOOKEEPER_QUORUM=localhost:2181

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Server
SERVER_PORT=8080

# MySQL (metastore)
MYSQL_HOST=localhost
MYSQL_PORT=3306
```

## Project Links

- [Documentation](https://actionbase.io/)
- [GitHub](https://github.com/kakao/actionbase)
- [Discussions](https://github.com/kakao/actionbase/discussions/)
