---
description: Clean up dead code, unused imports, and duplicates.
---

# Refactor Clean Command

Invoke the **refactor-cleaner** agent to clean up the codebase.

## What It Does

1. **Find unused code**
   - Unused imports
   - Unused private functions
   - Dead code branches

2. **Identify duplicates**
   - Similar functions
   - Repeated patterns

3. **Safe removal**
   - Verify no references
   - Run tests after each removal
   - Document deletions

## Process

1. Run detection tools:
   ```bash
   ./gradlew detekt
   cd cli && go vet ./...
   ```

2. Review findings

3. Remove in batches:
   - Unused imports first
   - Unused private functions
   - Dead code

4. Verify after each batch:
   ```bash
   ./gradlew build test
   cd cli && go build ./... && go test ./...
   ```

## Safety Rules

- Start with SAFE items (unused imports)
- Test after each removal
- Document all deletions
- Don't remove during active development
- Don't remove public APIs without checking

## NEVER Remove

- Storage client code
- Messaging producer/consumer
- Core model classes
- REST API endpoints
- CLI commands

## Usage

```
User: /refactor-clean

Agent:
## Cleanup Analysis

### Unused Imports Found
- core/src/.../Model.kt: 3 unused imports
- server/src/.../Controller.kt: 1 unused import

### Unused Private Functions
- core/src/.../Utils.kt: `legacyEncode()` - no callers

### Proposed Removals
1. Remove 4 unused imports
2. Remove 1 unused function

Proceed? (yes/no)
```
