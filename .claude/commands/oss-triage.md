---
description: Triage GitHub issues - classify, label, and prioritize.
---

# Triage Command

Help maintainers triage GitHub issues efficiently.

## Process

1. **Read Issue**
   ```bash
   gh issue view <number>
   ```

2. **Classify Type**
   - `bug` - Something isn't working
   - `feature` - New feature request
   - `question` - Support question
   - `docs` - Documentation improvement

3. **Assess Priority**
   - `priority/critical` - Security, data loss, crashes
   - `priority/high` - Major functionality broken
   - `priority/medium` - Important but not urgent
   - `priority/low` - Nice to have

4. **Add Labels**
   ```bash
   gh issue edit <number> --add-label "bug,priority/high,module/core"
   ```

5. **Respond** (if needed)
   - Thank the reporter
   - Ask clarifying questions
   - Link related issues

## Labels

### Type
- `bug`, `feature`, `question`, `docs`, `refactor`

### Priority
- `priority/critical`, `priority/high`, `priority/medium`, `priority/low`

### Module
- `module/core`, `module/engine`, `module/server`, `module/cli`, `module/docs`

### Status
- `needs-info`, `confirmed`, `wontfix`, `duplicate`

## Usage

```
User: /triage 123

Agent:
## Issue #123 Triage

### Summary
User reports mutation API returning 500 error with specific payload.

### Classification
- **Type**: bug
- **Priority**: high (API broken for valid input)
- **Module**: server

### Suggested Labels
gh issue edit 123 --add-label "bug,priority/high,module/server"

### Suggested Response
Thanks for reporting! I can reproduce this issue.
It looks like the validation is rejecting valid Unicode characters.
We'll fix this in the next patch release.

### Related
- Similar to #98 (Unicode handling)
```

## Quick Commands

```bash
# List open issues needing triage
gh issue list --label "needs-triage" --state open

# List by priority
gh issue list --label "priority/critical" --state open

# Close as duplicate
gh issue close <number> --reason "duplicate" --comment "Duplicate of #<other>"
```
