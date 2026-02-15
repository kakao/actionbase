# Claude Code Configuration

This directory contains Claude Code configuration for Actionbase.

## Structure

```
.claude/
├── agents/          # Custom agent definitions
├── codemaps/        # Architecture documentation
├── commands/        # Slash commands (/build, /test, /develop, etc.)
├── rules/           # Auto-loaded guidelines (coding, security, refactoring, etc.)
├── skills/          # Skill definitions (skill-name/SKILL.md)
├── settings.json    # Shared settings (permissions, hooks)
└── settings.local.json.template  # Local settings template
```

## Key Rules

| Rule | Purpose |
|------|---------|
| `refactoring.md` | One chain, one read. No 3-jump splits. Minimal diff. |
| `coding-standards.md` | File/function size limits, immutability, patterns |
| `coding-style.md` | Kotlin/Java/Go language-specific patterns |
| `performance.md` | Storage, messaging, reactive performance |
| `security.md` | Secret management, input validation |
| `testing-guide.md` | Data-driven E2E tests, TDD, @ObjectSource |

## Branch Policy

Changes to `.claude/` and `CLAUDE.md` are tracked on **`exp/claude-code-setup`** branch, not `main`.

### To contribute:

```bash
git worktree add ../actionbase-claude-config exp/claude-code-setup
cp -r .claude/* ../actionbase-claude-config/.claude/
cd ../actionbase-claude-config
git add .claude/ CLAUDE.md
git commit -m "chore(claude): update configuration"
git push
git worktree remove ../actionbase-claude-config
```

### To get latest:

```bash
git fetch origin
git restore --source=origin/exp/claude-code-setup -- .claude/ CLAUDE.md
```

See PR #91 for details: https://github.com/kakao/actionbase/pull/91
