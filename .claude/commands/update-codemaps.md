---
description: Update architecture documentation (codemaps).
---

# Update Codemaps Command

Generate and update codebase architecture documentation.

## Codemap Files

```
website/src/content/docs/
├── design/
│   ├── concepts.mdx      # Core concepts
│   ├── mutation.mdx      # Mutation flow
│   ├── query.mdx         # Query flow
│   ├── schema.mdx        # Schema design
│   ├── datastore.mdx     # Storage architecture
│   └── glossary.mdx      # Terms
├── internals/
│   └── encoding.mdx      # Row key encoding (FIXED)
└── api-references/
    ├── mutation.mdx      # Mutation API
    ├── query.mdx         # Query API
    └── metadata.mdx      # Metadata API
```

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

### Documentation Status
| Doc | Status | Action |
|-----|--------|--------|
| design/mutation.mdx | OUTDATED | Update flow diagram |
| api-references/mutation.mdx | OK | No changes |
| internals/encoding.mdx | FIXED | Skip (finalized) |

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
