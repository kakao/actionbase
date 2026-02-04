# Claude Code Configuration

This directory contains Claude Code configuration for Actionbase.

## Important: Contributing Changes

Changes to `.claude/` and `CLAUDE.md` are tracked on the **`exp/claude-code-setup`** branch, not `main`.

### To contribute changes:

```bash
# 1. Create worktree for the exp branch
git worktree add ../actionbase-claude-config exp/claude-code-setup

# 2. Copy your changes
cp -r .claude/* ../actionbase-claude-config/.claude/
cp CLAUDE.md ../actionbase-claude-config/

# 3. Commit and push from the worktree
cd ../actionbase-claude-config
git add .claude/ CLAUDE.md
git commit -m "chore(claude): update configuration"
git push

# 4. Clean up worktree (optional)
cd -
git worktree remove ../actionbase-claude-config
```

### To get latest configuration:

```bash
git fetch origin
git restore --source=origin/exp/claude-code-setup -- .claude/ CLAUDE.md
```

See PR #91 for details: https://github.com/kakao/actionbase/pull/91
