---
description: Run all tests across Gradle and Go modules.
---

# Test Command

Run all tests in the project.

## Commands

### Run All Tests

```bash
# Kotlin/Java (Gradle)
./gradlew test

# Go (CLI)
cd cli && go test ./...
```

### Run Specific Module

```bash
# Core module
./gradlew :core:test

# Engine module
./gradlew :engine:test

# Server module
./gradlew :server:test

# CLI
cd cli && go test ./cmd/...
cd cli && go test ./internal/...
```

### With Coverage

```bash
# Kotlin/Java
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Go
cd cli && go test -cover ./...
cd cli && go test -coverprofile=coverage.out ./...
go tool cover -html=coverage.out
```

### Verbose Output

```bash
# Gradle
./gradlew test --info

# Go
cd cli && go test -v ./...
```

## Expected Output

```
> Task :core:test
> Task :engine:test
> Task :server:test

BUILD SUCCESSFUL

cli/
PASS
ok      github.com/kakao/actionbase/cli/cmd      0.123s
ok      github.com/kakao/actionbase/cli/internal 0.456s
```

## Troubleshooting

### Tests Fail
1. Check error message
2. Run specific failing test
3. Debug with verbose output

### Slow Tests
1. Check for blocking I/O
2. Review test setup/teardown
3. Consider parallel execution
