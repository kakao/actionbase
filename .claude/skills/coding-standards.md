---
name: coding-standards
description: Universal coding standards, best practices, and patterns for Kotlin, Java, and Go development in Actionbase.
---

# Coding Standards & Best Practices

Universal coding standards applicable across all Actionbase modules.

## Code Quality Principles

### 1. Readability First
- Code is read more than written
- Clear variable and function names
- Self-documenting code preferred over comments
- Consistent formatting

### 2. KISS (Keep It Simple, Stupid)
- Simplest solution that works
- Avoid over-engineering
- No premature optimization
- Easy to understand > clever code

### 3. DRY (Don't Repeat Yourself)
- Extract common logic into functions
- Create reusable components
- Share utilities across modules
- Avoid copy-paste programming

### 4. YAGNI (You Aren't Gonna Need It)
- Don't build features before they're needed
- Avoid speculative generality
- Add complexity only when required
- Start simple, refactor when needed

## Kotlin Standards

### Variable Naming

```kotlin
// GOOD: Descriptive names
val userInteractionCount = 100
val isSchemaValid = true
val mutationResult = processMutation(mutation)

// BAD: Unclear names
val cnt = 100
val flag = true
val res = processMutation(mutation)
```

### Function Naming

```kotlin
// GOOD: Verb-noun pattern
suspend fun fetchUserInteractions(userId: String): List<Interaction> { }
fun calculateSimilarity(a: ByteArray, b: ByteArray): Double { }
fun isValidRowKey(key: String): Boolean { }

// BAD: Unclear or noun-only
suspend fun interactions(id: String): List<Interaction> { }
fun similarity(a: ByteArray, b: ByteArray): Double { }
fun rowKey(key: String): Boolean { }
```

### Immutability Pattern (CRITICAL)

```kotlin
// ALWAYS use immutable data classes
data class User(val id: String, val name: String)

// ALWAYS use copy() for updates
val updatedUser = user.copy(name = "New Name")

// NEVER use mutable properties
data class User(var id: String, var name: String)  // BAD

// PREFER immutable collections
val items: List<String> = listOf("a", "b")
val updatedItems = items + "c"

// AVOID mutable collections where possible
val items: MutableList<String> = mutableListOf()  // Only when necessary
```

### Null Safety

```kotlin
// GOOD: Safe null handling
fun processUser(user: User?): String {
    return user?.name ?: "Unknown"
}

// GOOD: Early return for null
fun processUser(user: User?): String {
    if (user == null) return "Unknown"
    return user.name
}

// BAD: Null assertion (avoid)
fun processUser(user: User?): String {
    return user!!.name  // Can throw NPE
}
```

### Error Handling

```kotlin
// GOOD: Using Result type
fun processData(data: String): Result<ProcessedData> {
    return runCatching {
        validateData(data)
        transformData(data)
    }
}

// Usage
processData(input)
    .onSuccess { result -> handleSuccess(result) }
    .onFailure { error -> handleError(error) }

// GOOD: Specific exceptions
class SchemaNotFoundException(message: String) : Exception(message)
class InvalidMutationException(message: String) : Exception(message)

// BAD: Catching generic Exception
try {
    processData(data)
} catch (e: Exception) {  // Too broad
    // Handle
}
```

### Extension Functions

```kotlin
// GOOD: Clear, focused extensions
fun String.toRowKey(): ByteArray = this.toByteArray(Charsets.UTF_8)

fun ByteArray.toRowKeyString(): String = String(this, Charsets.UTF_8)

fun List<Interaction>.filterByUser(userId: String): List<Interaction> =
    this.filter { it.userId == userId }
```

## Java Standards

### Naming Conventions

```java
// GOOD: Clear naming
public class UserInteractionRepository { }
public interface MutationProcessor { }
private static final int MAX_RETRY_COUNT = 3;

// Methods: verb-noun
public User findUserById(String id) { }
public void processInteraction(Interaction interaction) { }
```

### Optional Usage

```java
// GOOD: Proper Optional handling
public Optional<User> findById(String id) {
    return Optional.ofNullable(repository.get(id));
}

// Usage
findById(id)
    .map(User::getName)
    .orElse("Unknown");

// BAD: Optional.get() without check
findById(id).get();  // Can throw NoSuchElementException
```

### Stream API

```java
// GOOD: Clear stream operations
List<String> activeUserIds = users.stream()
    .filter(User::isActive)
    .map(User::getId)
    .collect(Collectors.toList());

// GOOD: Parallel for large datasets
List<Result> results = largeDataset.parallelStream()
    .map(this::processItem)
    .collect(Collectors.toList());
```

## Go Standards (CLI)

### Variable Naming

```go
// GOOD: Descriptive names
userID := "user123"
interactionCount := 100
isValid := true

// BAD: Single letter (except in loops)
u := "user123"
n := 100
```

### Function Naming

```go
// GOOD: Verb-noun or descriptive
func FetchUserInteractions(userID string) ([]Interaction, error) { }
func ValidateRowKey(key string) bool { }
func NewMutationClient(opts Options) *MutationClient { }

// Constructor pattern
func NewClient(config Config) (*Client, error) { }
```

### Error Handling (CRITICAL)

```go
// GOOD: Always check errors
result, err := doSomething()
if err != nil {
    return fmt.Errorf("failed to do something: %w", err)
}

// GOOD: Error wrapping
if err := process(data); err != nil {
    return fmt.Errorf("process failed for data %s: %w", data.ID, err)
}

// BAD: Ignoring errors
result, _ := doSomething()  // Never do this
```

### Defer for Cleanup

```go
// GOOD: Proper resource cleanup
file, err := os.Open(filename)
if err != nil {
    return err
}
defer file.Close()

// GOOD: HTTP response body
resp, err := http.Get(url)
if err != nil {
    return err
}
defer resp.Body.Close()
```

### Interface Design

```go
// GOOD: Small, focused interfaces
type Reader interface {
    Read(p []byte) (n int, err error)
}

type MutationProcessor interface {
    Process(mutation *Mutation) error
}

// BAD: Large interfaces
type Service interface {
    Process(...)
    Validate(...)
    Transform(...)
    Store(...)
    // 10 more methods...
}
```

## API Design Standards

### REST API Conventions

```
GET    /api/v1/schemas                # List schemas
GET    /api/v1/schemas/:name          # Get specific schema
POST   /api/v1/mutation               # Create mutation
GET    /api/v1/query                  # Query interactions
```

### Response Format

```kotlin
// GOOD: Consistent response structure
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

// Success response
return ResponseEntity.ok(ApiResponse(success = true, data = results))

// Error response
return ResponseEntity.badRequest()
    .body(ApiResponse<Nothing>(success = false, error = "Invalid schema"))
```

## File Organization

### Project Structure

```
core/src/main/kotlin/
├── model/                 # Data models
│   ├── Mutation.kt
│   ├── Query.kt
│   └── Schema.kt
├── encoding/              # Serialization
├── processor/             # Business logic
└── util/                  # Utilities

server/src/main/kotlin/
├── controller/            # REST endpoints
├── service/               # Business logic
├── config/               # Configuration
└── dto/                   # Data transfer objects

cli/
├── cmd/                   # CLI commands
├── internal/              # Internal packages
├── pkg/                   # Public packages
└── main.go               # Entry point
```

### File Naming

```
Kotlin: UserInteractionRepository.kt    # PascalCase
Java:   UserInteractionRepository.java  # PascalCase
Go:     user_interaction.go             # snake_case
Test:   UserInteractionRepositoryTest.kt
Go test: user_interaction_test.go
```

## Code Smell Detection

### 1. Long Functions
```kotlin
// BAD: Function > 50 lines
fun processAllData() {
    // 100 lines of code
}

// GOOD: Split into smaller functions
fun processAllData() {
    val validated = validateData()
    val transformed = transformData(validated)
    return persistData(transformed)
}
```

### 2. Deep Nesting
```kotlin
// BAD: 5+ levels of nesting
if (user != null) {
    if (user.isActive) {
        if (schema != null) {
            if (schema.isValid) {
                // Do something
            }
        }
    }
}

// GOOD: Early returns
if (user == null) return
if (!user.isActive) return
if (schema == null) return
if (!schema.isValid) return

// Do something
```

### 3. Magic Numbers
```kotlin
// BAD: Unexplained numbers
if (retryCount > 3) { }
delay(500)

// GOOD: Named constants
companion object {
    const val MAX_RETRIES = 3
    const val DEBOUNCE_DELAY_MS = 500L
}

if (retryCount > MAX_RETRIES) { }
delay(DEBOUNCE_DELAY_MS)
```

**Remember**: Code quality is not negotiable. Clear, maintainable code enables rapid development and confident refactoring.
