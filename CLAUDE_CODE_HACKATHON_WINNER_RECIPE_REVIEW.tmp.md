# Claude Code Hackathon Winner's Recipe Review (Temporary)

Adapting the hackathon winner's recipe for Actionbase.
Delete this file after review.

## Background

- Source: [everything-claude-code](https://github.com/affaan-m/everything-claude-code)
- Discussion: #90
- PR: #91

## Goal

Fine-tune for Actionbase tech stack:
- Kotlin/Java + Spring WebFlux
- Go + Cobra
- HBase + Kafka
- Gradle + Make

## Review Checklist

- [x] Relevant to Actionbase
- [x] Correct tech stack
- [x] No Node.js/React leftovers
- [x] English, community tone

## Files

### Root
- [x] `CLAUDE.md` - REWRITE (Actionbase context, Backend Language policy)

### Agents (9)
- [x] `planner.md` - EDIT (Storage/Messaging abstraction)
- [x] `architect.md` - EDIT (Storage/Messaging abstraction)
- [x] `code-reviewer.md` - EDIT (Storage/Messaging abstraction)
- [x] `security-reviewer.md` - EDIT (Storage/Messaging abstraction)
- [x] `build-error-resolver.md` - KEEP
- [x] `tdd-guide.md` - KEEP
- [x] `e2e-runner.md` - EDIT (minor)
- [x] `refactor-cleaner.md` - EDIT (minor)
- [x] `doc-updater.md` - EDIT (minor)

### Commands (10)
- [ ] `plan.md`
- [ ] `build.md`
- [ ] `build-fix.md`
- [ ] `tdd.md`
- [ ] `test.md`
- [ ] `code-review.md`
- [ ] `review.md`
- [ ] `refactor-clean.md`
- [ ] `update-docs.md`
- [ ] `e2e.md`

### Rules (8)
- [x] `security.md` - EDIT (generalized HBase to Storage)
- [x] `testing.md` - EDIT (Kotest/JUnit5, reference CLAUDE.md)
- [x] `coding-style.md` - REWRITE (Kotlin/Java/Go patterns from codebase)
- [x] `git-workflow.md` - EDIT (simplify, reference CLAUDE.md)
- [x] `performance.md` - EDIT (Storage/Messaging abstraction)
- [x] `agents.md` - KEEP
- [x] `patterns.md` - EDIT (simplify, reference CLAUDE.md)
- [x] `hooks.md` - KEEP

### Skills (4)
- [ ] `coding-standards.md`
- [ ] `backend-patterns.md`
- [ ] `cli-patterns.md`
- [ ] `actionbase-concepts.md`

## Actions

- `KEEP` - Good
- `EDIT` - Minor fix
- `REWRITE` - Major change
- `DELETE` - Remove

## Progress

| Category | Done | Total |
|----------|------|-------|
| Root     | 1    | 1     |
| Agents   | 9    | 9     |
| Commands | 0    | 10    |
| Rules    | 8    | 8     |
| Skills   | 0    | 4     |
| **Total**| 18   | 32    |
