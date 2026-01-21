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
   - `enhancement` - New feature or request
   - `question` - Further information is requested
   - `documentation` - Improvements or additions to documentation

3. **Identify Module**
   - `module:core` - Core module
   - `module:engine` - Engine module
   - `module:server` - Server module
   - `module:cli` - CLI module
   - `module:website` - Website/docs
   - `module:guides` - Tutorial guides

4. **Add Labels**
   ```bash
   gh issue edit <number> --add-label "bug,module:server"
   ```

5. **Respond** (if needed)
   - Thank the reporter
   - Ask clarifying questions
   - Link related issues

## Labels

### Type
| Label | Description |
|-------|-------------|
| `bug` | Something isn't working |
| `enhancement` | New feature or request |
| `documentation` | Improvements or additions to documentation |
| `question` | Further information is requested |
| `maintenance` | Maintenance work |

### Module
| Label | Description |
|-------|-------------|
| `module:core` | Changes related to the module `core` |
| `module:engine` | Changes related to the module `engine` |
| `module:server` | Changes related to the module `server` |
| `module:cli` | Changes related to the module `cli` |
| `module:website` | Changes related to the module `website` |
| `module:guides` | Changes related to the module `guides` |

### Status
| Label | Description |
|-------|-------------|
| `duplicate` | This issue or pull request already exists |
| `invalid` | This doesn't seem right |
| `wontfix` | This will not be worked on |
| `help wanted` | Community contributions are welcome |
| `good first issue` | Good for newcomers |
| `lgtm` | This PR has been approved by a maintainer |

### PR Size (automated)
| Label | Lines Changed |
|-------|---------------|
| `size:XS` | 0-9 |
| `size:S` | 10-29 |
| `size:M` | 30-99 |
| `size:L` | 100-499 |
| `size:XL` | 500-999 |
| `size:XXL` | 1000+ |

## Usage

```
User: /oss-triage 123

Agent:
## Issue #123 Triage

### Summary
User reports mutation API returning 500 error with specific payload.

### Classification
- **Type**: bug
- **Module**: server

### Suggested Labels
gh issue edit 123 --add-label "bug,module:server"

### Suggested Response
Thanks for reporting! I can reproduce this issue.
It looks like the validation is rejecting valid Unicode characters.
We'll fix this in the next patch release.

### Related
- Similar to #98 (Unicode handling)
```

## Quick Commands

```bash
# List open issues by type
gh issue list --label "bug" --state open
gh issue list --label "enhancement" --state open

# List by module
gh issue list --label "module:cli" --state open

# Good first issues
gh issue list --label "good first issue" --state open

# Close as duplicate
gh issue close <number> --reason "duplicate" --comment "Duplicate of #<other>"
```
