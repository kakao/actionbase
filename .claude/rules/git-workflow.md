# Git Workflow Guidelines

See `CLAUDE.md` for commit format and workflow details.

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

## Rules

- Never force push to main
- Require PR review before merge
- Ensure CI passes before merge
- Keep PRs focused and small

## Multi-Agent Parallel Work

Use **git worktree** to run multiple agents on different branches simultaneously:

```bash
# Create worktrees for parallel work
git worktree add ../actionbase-feature-a feature/branch-a
git worktree add ../actionbase-feature-b feature/branch-b

# List worktrees
git worktree list

# Remove when done
git worktree remove ../actionbase-feature-a
```

Benefits:
- Same repo, separate directories, separate branches
- No branch conflicts between agents
- Shared `.git` saves disk space

## Templates

See `CLAUDE.md` for template locations:
- PR: `.github/PULL_REQUEST_TEMPLATE.md`
- Issues: `.github/ISSUE_TEMPLATE/`
