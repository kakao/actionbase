# Coding Style Guidelines

## General Rules

- **File size**: Maximum 800 lines per file
- **Function size**: Maximum 50 lines per function
- **Nesting depth**: Maximum 4 levels
- **Immutability**: Prefer immutable data structures

See `CLAUDE.md` for Backend Language policy (Kotlin preferred).

## Kotlin Patterns

### Data Classes with Defaults
```kotlin
data class Edge(
    val version: Long,
    val source: Any,
    val target: Any,
    val properties: Map<String, Any?> = emptyMap(),
)
```

### Sealed Classes for Type Hierarchies
```kotlin
sealed class EdgeRecord {
    sealed class Key {
        data class CommonPrefix(val source: Any, val tableCode: Int, val typeCode: Byte)
    }
}
```

### Enum with Abstract Method
```kotlin
enum class PrimitiveType {
    INT { override fun cast(value: Any): Any = (value as Number).toInt() },
    LONG { override fun cast(value: Any): Any = (value as Number).toLong() };

    abstract fun cast(value: Any): Any
}
```

### Extension Functions in Objects
```kotlin
object MapperExtensions {
    fun ByteArrayBuffer.encodeKeyPrefix(source: Any): ByteArrayBuffer {
        // extension logic
        return this
    }
}
```

## Java Patterns

### Immutable Class with Final Fields
```java
public class JvmInfo {
    private final String runtimeVersion;
    private final String vmVersion;

    public JvmInfo(String runtimeVersion, String vmVersion) {
        this.runtimeVersion = runtimeVersion;
        this.vmVersion = vmVersion;
    }

    public String getRuntimeVersion() { return runtimeVersion; }
}
```

### Enum with Abstract Methods
```java
public enum Order {
    ASC {
        public int cmp(int cmp) { return cmp; }
        public byte apply(byte val) { return val; }
    },
    DESC {
        public int cmp(int cmp) { return -1 * cmp; }
        public byte apply(byte val) { return (byte) (~val); }
    };

    public abstract int cmp(int var1);
    public abstract byte apply(byte var1);
}
```

### Fluent Interface Pattern
```java
public interface ByteRange {
    ByteRange set(byte[] var1);
    ByteRange setOffset(int var1);
    ByteRange put(int var1, byte var2);
}
```

## Go Patterns

### Generic Utility Functions
```go
func FilterInPlace[T any](s []T, fn func(T) bool) []T {
    n := 0
    for _, v := range s {
        if fn(v) {
            s[n] = v
            n++
        }
    }
    return s[:n]
}
```

### Interface + Struct Embedding
```go
type TableCommandRunner interface {
    GetCurrentDatabase() string
    GetCurrentTable() string
}

type BaseCommand struct {
    client *client.ActionbaseClient
    runner TableCommandRunner
}

type Get struct {
    *BaseCommand  // Embedding for composition
}
```

### Command Pattern with Early Returns
```go
func (g *Get) Execute(args []string) *model.Response {
    if len(args) < 1 {
        return model.Fail("Usage: ...")
    }
    database, errResp := ValidateDatabase(g.runner)
    if errResp != nil {
        return errResp
    }
    return g.doExecute(database)
}
```

### Table-Driven Tests
```go
func TestProcess(t *testing.T) {
    tests := []struct{
        name  string
        input string
        want  string
    }{
        {"valid", "input", "output"},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // test
        })
    }
}
```

## Naming Conventions

- **Kotlin/Java**: `camelCase` for variables, `PascalCase` for classes
- **Go**: `camelCase` for private, `PascalCase` for public
- **Files**: `PascalCase.kt` for Kotlin, `snake_case.go` for Go

## Logging & Comments

See `CLAUDE.md` for logging guidelines.

- Explain WHY, not WHAT
- Document public APIs with KDoc/GoDoc
- Remove commented-out code (use git history)
