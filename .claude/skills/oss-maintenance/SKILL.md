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
labels: bug
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
labels: enhancement
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
| `enhancement` | #a2eeef | New feature or request |
| `documentation` | #0075ca | Improvements or additions to documentation |
| `question` | #d876e3 | Further information is requested |
| `maintenance` | #57075e | Maintenance work |

### Module Labels
| Label | Color | Description |
|-------|-------|-------------|
| `module:core` | #c08cf1 | Changes related to the module `core` |
| `module:engine` | #3bab6c | Changes related to the module `engine` |
| `module:server` | #95ea9a | Changes related to the module `server` |
| `module:cli` | #d5d792 | Changes related to the module `cli` |
| `module:website` | #d2cf42 | Changes related to the module `website` |
| `module:guides` | #0c24f0 | Changes related to the module `guides` |

### Status Labels
| Label | Color | Description |
|-------|-------|-------------|
| `duplicate` | #cfd3d7 | This issue or pull request already exists |
| `invalid` | #e4e669 | This doesn't seem right |
| `wontfix` | #ffffff | This will not be worked on |
| `help wanted` | #008672 | Community contributions are welcome |
| `good first issue` | #7057ff | Good for newcomers |
| `lgtm` | #238636 | This PR has been approved by a maintainer |

### PR Size Labels (automated)
| Label | Color | Description |
|-------|-------|-------------|
| `size:XS` | #00ff00 | 0-9 lines changed |
| `size:S` | #77b800 | 10-29 lines changed |
| `size:M` | #ebb800 | 30-99 lines changed |
| `size:L` | #eb9500 | 100-499 lines changed |
| `size:XL` | #ff823f | 500-999 lines changed |
| `size:XXL` | #ffb8b8 | 1000+ lines changed |

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
- Critical bugs
- Issues with `help wanted` label

## Security Policy

Located in `SECURITY.md`:
- Supported versions
- How to report vulnerabilities
- Response timeline expectations
- Disclosure policy
