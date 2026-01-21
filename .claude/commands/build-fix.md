---
description: Incrementally fix Gradle and Go build errors with minimal changes.
---

# Build and Fix

Incrementally fix Kotlin/Java (Gradle) and Go (CLI) build errors:

## Workflow

1. **Run builds:**
   - `./gradlew build` for Kotlin/Java
   - `cd cli && go build ./...` for Go CLI

2. **Parse error output:**
   - Group by file
   - Sort by severity (errors before warnings)

3. **For each error:**
   - Show error context (5 lines before/after)
   - Explain the issue
   - Propose minimal fix
   - Apply fix
   - Re-run build
   - Verify error resolved

4. **Stop if:**
   - Fix introduces new errors
   - Same error persists after 3 attempts
   - User requests pause

5. **Show summary:**
   - Errors fixed
   - Errors remaining
   - New errors introduced

## Commands

```bash
# Kotlin/Java
./gradlew build
./gradlew compileKotlin compileJava

# Specific module
./gradlew :core:build
./gradlew :server:build

# Go CLI
cd cli && go build ./...
cd cli && go vet ./...
```

## Common Fixes

**Kotlin:**
- Null safety: Add `?` or null check
- Type mismatch: Add explicit type or cast
- Missing import: Add import statement

**Go:**
- Unused import: Remove import
- Undefined variable: Declare or fix typo
- Missing return: Add return statement

## Important

Fix one error at a time for safety!
Make minimal changes - don't refactor!
