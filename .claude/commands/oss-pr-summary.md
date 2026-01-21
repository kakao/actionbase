---
description: Summarize PR changes for review or release notes.
---

# PR Summary Command

Generate concise summaries of pull request changes.

## Process

1. **Get PR Info**
   ```bash
   gh pr view <number> --json title,body,files,commits
   gh pr diff <number>
   ```

2. **Analyze Changes**
   - What modules are affected?
   - What's the nature of changes?
   - Any breaking changes?

3. **Generate Summary**
   - One-line summary
   - Bullet points of key changes
   - Impact assessment

## Usage

```
User: /pr-summary 91

Agent:
## PR #91 Summary

### One-Line
Add Claude Code configuration for AI-assisted development.

### Changes
- **New**: CLAUDE.md project configuration
- **New**: 9 agents for code review, planning, testing
- **New**: 12 slash commands (/plan, /build, /test, etc.)
- **New**: 6 codemaps for architecture documentation

### Files Changed
- 40 files added
- 0 files modified
- 0 files deleted

### Modules Affected
- `.claude/` (new directory)
- Root (CLAUDE.md)

### Breaking Changes
None

### Review Notes
- Experimental configuration on `exp/claude-code-setup` branch
- Requires Claude Code CLI to use
```

## For Release Notes

```
User: /pr-summary 91 --release

Agent:
### Claude Code Integration (Experimental)

Added AI-assisted development configuration:
- Project context and coding guidelines
- Automated code review and planning agents
- Build, test, and documentation commands

See Discussion #90 for details.
```

## Quick Commands

```bash
# View PR details
gh pr view <number>

# View PR diff
gh pr diff <number>

# List PR files
gh pr view <number> --json files --jq '.files[].path'

# Get commits in PR
gh pr view <number> --json commits --jq '.commits[].messageHeadline'
```
