---
description: Update documentation to match current code state.
---

# Update Docs Command

Invoke the **doc-updater** agent to refresh documentation.

## What It Updates

### Website (Astro/Starlight)
Location: `website/src/content/docs/`

- API references
- Guides and tutorials
- Design documentation
- Operations guides

### Code Documentation
- KDoc comments (Kotlin)
- JavaDoc comments (Java)
- GoDoc comments (Go)

## Process

1. **Identify stale docs**
   ```bash
   git log --oneline -20  # Check recent changes
   ```

2. **Update affected docs**
   - Match code changes
   - Update examples
   - Verify accuracy

3. **Build and verify**
   ```bash
   cd website && npm run build
   cd website && npm run dev  # Preview
   ```

## Documentation Structure

```
website/src/content/docs/
├── index.mdx           # Home
├── quick-start.mdx     # Getting started
├── design/             # Architecture
├── api-references/     # API docs
├── guides/             # Tutorials
├── operations/         # Ops guides
└── provisioning/       # Deployment
```

## Usage

```
User: /update-docs

Agent:
## Documentation Analysis

### Recent Code Changes
- Added new endpoint: POST /api/v1/schema
- Updated CLI command: actionbase schema create

### Docs Needing Update
1. api-references/mutation.mdx - new endpoint
2. operations/cli.mdx - new command

### Updates Applied
- Added POST /api/v1/schema endpoint documentation
- Added `schema create` CLI command documentation

Build: website && npm run build ... SUCCESS
```
