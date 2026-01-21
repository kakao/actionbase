# Testing Requirements

## Test Coverage

Aim for good coverage on critical paths.

Test Types (ALL required for new features):
1. **Unit Tests** - Individual functions, classes
2. **Integration Tests** - API endpoints, HBase operations
3. **CLI Tests** - Command execution

## Test-Driven Development

Recommended workflow:
1. Write test first (RED)
2. Run test - it should FAIL
3. Write minimal implementation (GREEN)
4. Run test - it should PASS
5. Refactor (IMPROVE)
6. Verify coverage

## Commands

```bash
# Kotlin/Java
./gradlew test
./gradlew :core:test
./gradlew :server:test

# Go CLI
cd cli && go test ./...
cd cli && go test -cover ./...

# With coverage report
./gradlew test jacocoTestReport
```

## Troubleshooting Test Failures

1. Use **tdd-guide** agent
2. Check test isolation
3. Verify mocks are correct
4. Fix implementation, not tests (unless tests are wrong)

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features
- **e2e-runner** - Integration testing specialist
