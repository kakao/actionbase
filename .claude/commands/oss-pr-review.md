---
description: Review PR as maintainer - process, guidelines, and code quality.
---

# OSS PR Review Command

Review a pull request from maintainer perspective.

## Process

1. **Get PR Info**
   ```bash
   gh pr view <number> --json title,body,files,commits,reviews,checks
   ```

2. **Check Process**
   - PR description adequate?
   - Related issue linked?
   - CI status?

3. **Run Code Review**
   ```
   /code-review
   ```

4. **Provide Recommendation**
   - APPROVE / REQUEST_CHANGES / COMMENT

## Checklist

### PR Process
- [ ] Title follows conventional commit format
- [ ] Description explains what and why
- [ ] Related issue linked (Closes #xxx)
- [ ] No unrelated changes

### CI/Tests
- [ ] CI passing
- [ ] Tests included (if applicable)
- [ ] No test regressions

### Code Quality (via /code-review)
- [ ] No security issues
- [ ] Follows coding style
- [ ] Proper error handling

### Documentation
- [ ] README updated (if needed)
- [ ] API docs updated (if needed)

## Usage

```
User: /oss-pr-review 42

Agent:
## PR #42 Review

### PR Info
- Title: feat(engine): add globalMutationMode configuration
- Author: eazyhozy
- Files: 5 changed
- CI: passing

### Process Checklist
- [x] Title follows conventional commit format
- [x] Description explains changes
- [x] Related issue: #41
- [x] Focused changes

### CI/Tests
- [x] CI passing
- [ ] Tests included - MISSING
- [x] No regressions

### Code Review
Running /code-review...

[MEDIUM] Missing test coverage
File: engine/src/.../MutationMode.kt
Suggest: Add unit tests for new configuration

### Recommendation: REQUEST_CHANGES

### Suggested Response
Thanks for the PR! The implementation looks good.

Before merging, could you add tests for the new `globalMutationMode` configuration?
- Unit test for config parsing
- Integration test for mode switching

Let me know if you have questions!
```

## Quick Commands

```bash
# View PR with checks
gh pr view <number> --json checks

# View PR reviews
gh pr view <number> --json reviews

# Add review
gh pr review <number> --approve
gh pr review <number> --request-changes --body "feedback"
gh pr review <number> --comment --body "comment"
```

## Response Templates

### Approve
```
LGTM! Thanks for the contribution.

- Code looks clean
- Tests pass
- Documentation updated

Merging now.
```

### Request Changes
```
Thanks for the PR! A few things before we can merge:

- [ ] Add tests for X
- [ ] Update docs for Y

Let me know if you have questions!
```

### First-Time Contributor
```
Welcome to Actionbase! Thanks for your first PR.

[feedback]

Looking forward to merging this!
```
