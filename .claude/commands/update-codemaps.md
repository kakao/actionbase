---
description: Update architecture documentation (codemaps).
---

# Update Codemaps Command

Generate and update codebase architecture documentation.

## Codemap Files

```
.claude/codemaps/
├── architecture.md   # Overall architecture
├── core.md           # Core module (model, encoding)
├── engine.md         # Engine module (storage, messaging)
├── server.md         # Server module (REST API)
├── cli.md            # Go CLI
└── data.md           # Data model, storage format
```

Reference docs (user-facing):
- `website/src/content/docs/internals/encoding.mdx` - Row key encoding (FIXED)

## Process

1. **Analyze Changes**
   - Check modified source files
   - Identify architectural changes

2. **Compare with Docs**
   - Find outdated sections
   - Calculate change percentage

3. **Approval Gate**
   - If changes > 30%, request user approval
   - Show diff summary before updating

4. **Update Docs**
   - Update relevant mdx files
   - Add timestamp to changelog

## Module Structure

```
core/       # Data model, mutation, query, encoding
engine/     # Storage and Messaging bindings
server/     # REST API (Spring WebFlux)
cli/        # Command-line client (Go)
website/    # Documentation (Astro/Starlight)
```

## Usage

```
User: /update-codemaps

Agent:
## Codemap Analysis

### Recent Changes
- core/src/.../MutationProcessor.kt (modified)
- server/src/.../MutationController.kt (modified)

### Codemap Status
| File | Status | Action |
|------|--------|--------|
| codemaps/engine.md | OUTDATED | Update storage interface |
| codemaps/server.md | OK | No changes |
| codemaps/data.md | FIXED | Skip (encoding finalized) |

### Change Summary
- 2 files need updates
- Estimated change: 15%

Proceed with updates? (yes/no)
```

## Rules

- Never modify `internals/encoding.mdx` (row key format is finalized)
- Keep docs concise and accurate
- Use diagrams where helpful
- Cross-reference related docs
