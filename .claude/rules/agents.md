# Agent Usage Guidelines

## Available Agents

### Planning Agents
- **planner**: Use for feature planning, create implementation plans
- **architect**: Use for system design, architecture decisions

### Code Quality Agents
- **code-reviewer**: Use after writing code, reviews for quality/security
- **security-reviewer**: Use for security-focused review
- **refactor-cleaner**: Use for dead code cleanup

### Build & Test Agents
- **build-error-resolver**: Use when build fails, fixes compilation errors
- **tdd-guide**: Use for test-driven development
- **e2e-runner**: Use for integration testing

### Documentation Agents
- **doc-updater**: Use to update documentation

## When to Use Agents

### ALWAYS use agents for:
- Planning new features (planner)
- Architectural decisions (architect)
- Code review (code-reviewer)
- Security-sensitive code (security-reviewer)

### Use agents PROACTIVELY for:
- Build errors (build-error-resolver)
- New feature implementation (tdd-guide)
- Documentation updates (doc-updater)

## Agent Commands

```
/plan       - Invoke planner
/review     - Invoke code-reviewer
/tdd        - Invoke tdd-guide
/build-fix  - Invoke build-error-resolver
```

## Best Practices

1. **Plan before coding**: Use `/plan` for complex features
2. **Review after coding**: Use `/review` before committing
3. **Test-driven**: Use `/tdd` for new features
4. **Fix builds quickly**: Use `/build-fix` for compilation errors
