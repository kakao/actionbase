---
description: Comprehensive code review for quality, security, and maintainability.
---

# Code Review Command

Invoke the **code-reviewer** agent to review recent changes.

## What It Checks

### Security (CRITICAL)
- Hardcoded credentials
- Storage injection risks
- Input validation
- Authentication/authorization

### Code Quality (HIGH)
- Function length (>50 lines)
- File length (>800 lines)
- Deep nesting (>4 levels)
- Error handling
- Test coverage

### Performance (MEDIUM)
- Storage scan efficiency
- N+1 queries
- Unbounded operations
- Blocking in reactive code

### Best Practices
- Naming conventions
- Documentation
- Code duplication

## Review Process

1. Run `git diff` to see changes
2. Review each modified file
3. Check against standards
4. Report issues by priority
5. Suggest fixes

## Approval Criteria

- **APPROVE**: No CRITICAL or HIGH issues
- **CAUTION**: MEDIUM issues only
- **BLOCK**: CRITICAL or HIGH issues found

## Usage

```
User: /code-review

Agent:
## Code Review: Recent Changes

### Files Reviewed
- core/src/.../Mutation.kt
- server/src/.../MutationController.kt

### Issues Found

[CRITICAL] Missing input validation
File: server/src/.../MutationController.kt:45
Issue: User input passed directly to storage
Fix: Add validation before processing

[MEDIUM] Function too long
File: core/src/.../MutationProcessor.kt:20-85
Issue: 65 lines, recommend <50
Fix: Extract helper methods

### Recommendation: BLOCK
Fix critical issue before merging.
```

## Related Agents

Invokes: `.claude/agents/code-reviewer.md`
