---
description: Analyze test coverage and identify gaps.
---

# Test Coverage Command

Analyze test coverage and identify areas needing more tests.

## Commands

```bash
# Kotlin/Java coverage (JaCoCo)
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html

# Go coverage
cd cli && go test -cover ./...

# Go coverage with HTML report
cd cli && go test -coverprofile=coverage.out ./... && go tool cover -html=coverage.out
```

## Coverage Goals

Target: 80% coverage on critical paths.

## Analysis Process

1. Run coverage reports
2. Identify files below threshold
3. Prioritize by criticality:
   - Core module (mutation, query, schema)
   - Engine module (storage, messaging)
   - Server module (API endpoints)
   - CLI module (commands)

## What to Test

### High Priority
- Business logic in core module
- API endpoint handlers
- Error handling paths
- Input validation

### Medium Priority
- Utility functions
- Data transformations
- Edge cases

### Lower Priority
- Configuration classes
- Simple DTOs

## Usage

```
User: /test-coverage

Agent:
## Coverage Analysis

### Kotlin/Java (JaCoCo)
./gradlew test jacocoTestReport

Overall: 75%

| Module | Coverage | Status |
|--------|----------|--------|
| core   | 82%      | OK     |
| engine | 71%      | BELOW  |
| server | 78%      | BELOW  |

### Go CLI
cd cli && go test -cover ./...

Overall: 68%

| Package | Coverage | Status |
|---------|----------|--------|
| cmd     | 72%      | BELOW  |
| client  | 65%      | BELOW  |

### Recommended Actions
1. Add tests for engine/StorageClient error paths
2. Add tests for server validation logic
3. Add CLI integration tests for mutation command
```
