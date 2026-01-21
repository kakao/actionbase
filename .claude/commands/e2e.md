---
description: Run end-to-end integration tests.
---

# E2E Command

Run end-to-end tests for API and CLI.

## What It Tests

1. **API Integration**
   - REST endpoints
   - Request/response flow
   - Error handling

2. **CLI Integration**
   - Command execution
   - Server communication
   - Output formatting

## Commands

### Run E2E Tests

```bash
# API integration tests
./gradlew :server:integrationTest

# CLI E2E tests
cd cli && go test -tags=e2e ./...
```

### With Test Containers

```bash
# Starts HBase/Kafka containers automatically
./gradlew :server:integrationTest -Ptestcontainers
```

## Test Categories

### API Tests
- `MutationApiIntegrationTest` - Mutation endpoints
- `QueryApiIntegrationTest` - Query endpoints
- `SchemaApiIntegrationTest` - Schema management

### CLI Tests
- `mutation_test.go` - Mutation commands
- `query_test.go` - Query commands
- `shell_test.go` - Interactive shell

## Usage

```
User: /e2e

Agent:
## Running E2E Tests

### API Tests
./gradlew :server:integrationTest

> Task :server:integrationTest
MutationApiIntegrationTest > POST mutation creates interaction PASSED
MutationApiIntegrationTest > POST mutation validation error PASSED
QueryApiIntegrationTest > GET query returns results PASSED
...

### CLI Tests
cd cli && go test -tags=e2e ./...

PASS
ok      github.com/kakao/actionbase/cli/e2e  1.234s

### Summary
- API Tests: 12 passed
- CLI Tests: 8 passed
- Total: 20 passed, 0 failed
```
