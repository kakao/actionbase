# Git Workflow Guidelines

## Commit Message Format

```
<type>: <description>

[optional body]

[optional footer]
```

### Types
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `docs`: Documentation
- `test`: Adding/updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvement

### Examples

```
feat: add bookmark schema support

- Add BookmarkSchema class
- Update SchemaRegistry
- Add REST endpoints

Closes #123
```

```
fix: handle null userId in mutation

Added validation to reject mutations with null userId
```

## Branch Naming

```
feature/add-bookmark-schema
fix/null-userid-validation
refactor/simplify-query-builder
```

## Pull Request Workflow

1. Create feature branch from main
2. Make changes with meaningful commits
3. Push and create PR
4. Wait for review approval
5. Merge to main

## PR Description Template

```markdown
## Summary
Brief description of changes.

## Changes
- Added X
- Updated Y
- Fixed Z

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Related Issues
Closes #123
```

## Rules

- Never force push to main
- Require PR review before merge
- Ensure CI passes before merge
- Keep PRs focused and small
