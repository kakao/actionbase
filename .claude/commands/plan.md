---
description: Restate requirements, assess risks, and create step-by-step implementation plan. WAIT for user CONFIRM before touching any code.
---

# Plan Command

This command invokes the **planner** agent to create a comprehensive implementation plan before writing any code.

## What This Command Does

1. **Restate Requirements** - Clarify what needs to be built
2. **Identify Risks** - Surface potential issues and blockers
3. **Create Step Plan** - Break down implementation into phases
4. **Wait for Confirmation** - MUST receive user approval before proceeding

## When to Use

Use `/plan` when:
- Starting a new feature
- Making significant architectural changes
- Working on complex refactoring
- Multiple modules will be affected (core, engine, server, cli)
- Requirements are unclear or ambiguous

## How It Works

The planner agent will:

1. **Analyze the request** and restate requirements in clear terms
2. **Break down into phases** with specific, actionable steps
3. **Identify dependencies** between modules (core -> engine -> server)
4. **Assess risks** and potential blockers
5. **Estimate complexity** (High/Medium/Low)
6. **Present the plan** and WAIT for your explicit confirmation

## Example Usage

```
User: /plan I need to add a new schema type for "bookmarks"

Agent (planner):
# Implementation Plan: Add Bookmarks Schema Type

## Requirements Restatement
- Add support for "bookmarks" schema type
- Users can bookmark content (similar to likes)
- Support create/delete mutations
- Support query for bookmarked items

## Implementation Phases

### Phase 1: Core Module
- Add BookmarkSchema definition in core/src/.../schema/
- Update SchemaRegistry to include bookmarks
- Add validation rules for bookmark mutations

### Phase 2: Engine Module
- Add storage configuration for bookmarks
- Update key builder for bookmark pattern
- Add messaging topic for bookmark events

### Phase 3: Server Module
- Add REST endpoints for bookmark operations
- Update API documentation
- Add request/response DTOs

### Phase 4: CLI Module
- Add bookmark commands to CLI
- Update help documentation

### Phase 5: Testing
- Unit tests for core module
- Integration tests for API
- CLI tests

## Dependencies
- Storage table creation
- Messaging topic setup
- Schema registry update

## Risks
- MEDIUM: Storage table creation in production
- LOW: Migration needed for existing data
- LOW: API versioning considerations

## Estimated Complexity: MEDIUM

**WAITING FOR CONFIRMATION**: Proceed with this plan? (yes/no/modify)
```

## Important Notes

**CRITICAL**: The planner agent will **NOT** write any code until you explicitly confirm the plan.

## Related Agents

This command invokes the `planner` agent located at:
`.claude/agents/planner.md`
