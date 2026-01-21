# Testing Requirements

## Test Framework

- Existing: Kotest (legacy)
- New tests: JUnit 5

## Test Types

ALL required for new features:
1. **Unit Tests** - Individual functions, classes
2. **Integration Tests** - API endpoints, Storage operations
3. **CLI Tests** - Command execution

## Test Structure

See `CLAUDE.md` for patterns:
- Given/When/Then structure
- `@ParameterizedTest` for same logic, different inputs

## Test-Driven Development

1. Write test first (RED)
2. Run test - it should FAIL
3. Write minimal implementation (GREEN)
4. Run test - it should PASS
5. Refactor (IMPROVE)

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features
- **e2e-runner** - Integration testing specialist
