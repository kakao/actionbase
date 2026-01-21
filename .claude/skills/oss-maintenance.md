---
name: oss-maintenance
description: Open source maintenance patterns - issue templates, PR workflows, labeling, and release processes.
---

# Open Source Maintenance

Patterns and practices for maintaining Actionbase as an open source project.

## Issue Templates

Located in `.github/ISSUE_TEMPLATE/`:

### Bug Report
```markdown
---
name: Bug Report
about: Report something that isn't working
labels: bug, needs-triage
---

## Description
Brief description of the bug.

## Steps to Reproduce
1. Step one
2. Step two
3. ...

## Expected Behavior
What should happen.

## Actual Behavior
What actually happens.

## Environment
- Actionbase version:
- OS:
- Java/Go version:
```

### Feature Request
```markdown
---
name: Feature Request
about: Suggest a new feature
labels: feature, needs-triage
---

## Problem
What problem does this solve?

## Proposed Solution
How should it work?

## Alternatives Considered
Other approaches you've thought about.

## Additional Context
Any other information.
```

## PR Template

Located in `.github/PULL_REQUEST_TEMPLATE.md`:

```markdown
## Summary
Brief description of changes.

## Changes
- Change 1
- Change 2

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing done

## Checklist
- [ ] Code follows style guidelines
- [ ] Documentation updated
- [ ] No breaking changes (or documented)

## Related Issues
Closes #<number>
```

## Label System

### Type Labels
| Label | Color | Description |
|-------|-------|-------------|
| `bug` | #d73a4a | Something isn't working |
| `feature` | #0075ca | New feature request |
| `docs` | #0052cc | Documentation only |
| `refactor` | #fbca04 | Code improvement |
| `question` | #d876e3 | Support question |

### Priority Labels
| Label | Color | Description |
|-------|-------|-------------|
| `priority/critical` | #b60205 | Urgent, security/data issues |
| `priority/high` | #d93f0b | Important, major functionality |
| `priority/medium` | #fbca04 | Normal priority |
| `priority/low` | #0e8a16 | Nice to have |

### Module Labels
| Label | Color | Description |
|-------|-------|-------------|
| `module/core` | #c5def5 | Core module |
| `module/engine` | #c5def5 | Engine module |
| `module/server` | #c5def5 | Server module |
| `module/cli` | #c5def5 | CLI module |
| `module/docs` | #c5def5 | Documentation |

### Status Labels
| Label | Color | Description |
|-------|-------|-------------|
| `needs-triage` | #ededed | Needs maintainer review |
| `needs-info` | #ededed | Waiting for reporter |
| `confirmed` | #0e8a16 | Verified by maintainer |
| `in-progress` | #fbca04 | Being worked on |
| `help-wanted` | #008672 | Open for contribution |
| `good-first-issue` | #7057ff | Good for newcomers |

## Release Process

### Versioning (SemVer)
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Release Checklist
1. Update version in build files
2. Update CHANGELOG.md
3. Create release branch (if major/minor)
4. Run full test suite
5. Create GitHub release with notes
6. Publish artifacts

### Release Notes Template
```markdown
## [1.2.0] - 2024-01-15

### Added
- New feature X (#123)
- Support for Y (#124)

### Changed
- Improved performance of Z (#125)

### Fixed
- Bug in mutation handling (#126)

### Breaking Changes
- API endpoint renamed from /old to /new (#127)
  - Migration: Update client calls to use new endpoint
```

## Stale Issue Policy

Issues are marked stale after 90 days of inactivity:
- Bot adds `stale` label
- Bot comments with warning
- Closed after 14 more days if no response

Exceptions:
- `priority/critical` or `priority/high`
- `confirmed` bugs
- Issues with `keep-open` label

## Security Policy

Located in `SECURITY.md`:
- Supported versions
- How to report vulnerabilities
- Response timeline expectations
- Disclosure policy
