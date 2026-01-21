---
name: coding-standards
description: Universal coding standards, best practices, and patterns for Kotlin, Java, and Go development in Actionbase.
---

# Coding Standards

See `CLAUDE.md` for code style, patterns, and build commands.
See `.claude/rules/coding-style.md` for detailed guidelines.

## Core Principles

1. **Readability First** - Clear names, self-documenting code
2. **KISS** - Simplest solution that works
3. **DRY** - Extract common logic
4. **YAGNI** - Don't build before needed

## Kotlin Quick Reference

```kotlin
// Immutable data classes (CRITICAL)
data class User(val id: String, val name: String)
val updated = user.copy(name = "New")

// Null safety
fun process(user: User?) = user?.name ?: "Unknown"

// Early returns
if (user == null) return
if (!isValid) return
```

## Go Quick Reference

```go
// Always check errors (CRITICAL)
result, err := doSomething()
if err != nil {
    return fmt.Errorf("failed: %w", err)
}

// Defer for cleanup
file, err := os.Open(filename)
if err != nil { return err }
defer file.Close()
```

## Code Smells

| Smell | Fix |
|-------|-----|
| Function > 50 lines | Split into smaller functions |
| Nesting > 4 levels | Use early returns |
| Magic numbers | Use named constants |
| Copy-paste code | Extract to shared function |

## File Limits

- **Max lines per file**: 800
- **Max lines per function**: 50
- **Max nesting depth**: 4
