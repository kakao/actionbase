# Release Policy

Actionbase follows [Semantic Versioning](https://semver.org/) (SemVer) to communicate changes clearly.

## Version scheme

Versions follow the format `MAJOR.MINOR.PATCH`:

- **MAJOR**: Incompatible API changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

Example: `1.2.3` → Major 1, Minor 2, Patch 3

## Current stage (0.x.x)

Actionbase has been running in production internally at Kakao. The open-source release is in preparation. During the `0.x.x` phase:

- APIs may change between releases
- Installation and operations guides are being prepared
- Feedback and experimentation are welcome

The `1.0.0` release will mark the point where external users can deploy Actionbase in production environments.

## Release workflow

Releases are automated via GitHub Actions. Development uses `-SNAPSHOT` suffixes.

### Branch model

```
main:    A ─ B ─ C ─ D ─ E ─ ...          (0.3.0-SNAPSHOT)
                 │
          tag v0.2.0
                 └── 0.2.x:  C ── F ──    (0.2.1-SNAPSHOT)
                                   │
                            tag v0.2.1
```

- `main` — active development, always `X.Y.0-SNAPSHOT`
- `X.Y.x` — release branch, created automatically on minor release

### Minor release

Three manual steps, everything else is automatic.

```
1. [manual]    Actions → "Bump Version" → version: 0.2.0
               → PR: "Bump version to 0.2.0"
2. [manual]    Merge PR
               → (auto) tag v0.2.0
               → (auto) create 0.2.x branch (0.2.1-SNAPSHOT)
               → (auto) PR: "Bump version to 0.3.0-SNAPSHOT"
3. [manual]    Merge snapshot PR
               → main is now 0.3.0-SNAPSHOT
```

### Patch release

Two manual steps. Fixes go to `main` first, then cherry-pick to the release branch.

```
1. [manual]    Actions → "Bump Version" → version: 0.2.1, branch: 0.2.x
               → PR: "Bump version to 0.2.1" (against 0.2.x)
2. [manual]    Merge PR
               → (auto) tag v0.2.1
               → (auto) 0.2.x bumped to 0.2.2-SNAPSHOT
```

### Upstream-first

Fixes go to `main` first, then cherry-pick to release branches.

```
✅  main → cherry-pick → 0.2.x
❌  commit directly to 0.2.x only
```

Exception: when `main` has diverged significantly and cherry-pick is not feasible, apply directly to the release branch with a note in the commit message.

## Compatibility promise

The following applies **after 1.0.0**:

- **PATCH** releases are always safe to upgrade
- **MINOR** releases add features without breaking existing functionality
- **MAJOR** releases may include breaking changes; migration guides will be provided

## Release candidates

Before a release, one or more release candidates (`-rc.N`) may be published for community testing and feedback.

To participate:

- Test RC versions in non-production environments
- Report issues on [GitHub Issues](https://github.com/kakao/actionbase/issues)
- Share feedback on [GitHub Discussions](https://github.com/kakao/actionbase/discussions)

## Support policy

Currently, only the **latest release** is actively supported with bug fixes and security patches.

As the project matures, we may introduce Long-Term Support (LTS) releases with extended maintenance windows.

## Release artifacts

All modules share the same version and are released together.

| Module     | Artifact     | Distribution                                |
| ---------- | ------------ | ------------------------------------------- |
| **core**   | Java library | GitHub Packages (Maven Central after 1.0.0) |
| **server** | Docker image | `ghcr.io/kakao/actionbase`                  |
| **cli**    | Binary       | GitHub Releases                             |

Release announcements are posted in [GitHub Releases](https://github.com/kakao/actionbase/releases).
