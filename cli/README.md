# Actionbase CLI

The Actionbase CLI is a command-line interface for managing and maintaining Actionbase.

## Development Setup

### Requirements

- Go 1.19 or higher
- Make (optional)

### Local Build

```bash
# Download dependencies
make deps

# Build
make build

# Run
make run
```

Or use Go commands directly:

```bash
go build -o build/actionbase ./cmd/actionbase
./build/actionbase
```

## Release Guide

Releases are automated by the [Release CLI workflow](../.github/workflows/release-cli.yml).

### 1. Push a Version Tag

```bash
git tag cli/1.0.0
git push origin cli/1.0.0
```

Use the `cli/X.Y.Z` form with no `v` prefix. The CLI prepends `v` itself when reporting
`--version`, and this matches the existing `cli/0.0.1` tag.

### 2. Publish the Draft Release

The workflow cross-compiles every platform, packages the archives, and attaches them to a
**draft** release:

- `actionbase_{VERSION}_darwin_amd64.tar.gz`
- `actionbase_{VERSION}_darwin_arm64.tar.gz`
- `actionbase_{VERSION}_linux_amd64.tar.gz`
- `actionbase_{VERSION}_windows_amd64.zip`
- `checksums.txt`

Each archive holds a single directory named after the archive, with the binary inside.

Review the draft, write the release notes, and click **Publish**. Leave *Set as the latest
release* unchecked, so the badge stays on the server release.

Afterwards, update `Formula/actionbase.rb` to point at the new tag. Its `sha256` is the hash
of GitHub's generated **source** tarball, not of any archive listed in `checksums.txt`:

```bash
curl -sL https://github.com/kakao/actionbase/archive/refs/tags/cli/1.0.0.tar.gz | shasum -a 256
```

To reproduce the archives locally:

```bash
cd cli
make package VERSION=1.0.0
```

### 3. Binary Filename Convention

Binary files uploaded to GitHub Release must follow this naming format:

- `actionbase-{os}-{arch}` (Linux, macOS)
- `actionbase-{os}-{arch}.exe` (Windows)

Examples:
- `actionbase-linux-amd64`
- `actionbase-darwin-amd64`
- `actionbase-darwin-arm64`
- `actionbase-windows-amd64.exe`

These filenames are used by the `install.sh` script to automatically download the correct binary.

### 4. Verify Installation Script

The `install.sh` script downloads binaries using the following URL pattern:

```
https://github.com/kakao/actionbase/releases/download/v{VERSION}/actionbase-{OS}-{ARCH}
```

Examples:
- `https://github.com/kakao/actionbase/releases/download/v1.0.0/actionbase-darwin-arm64`
- `https://github.com/kakao/actionbase/releases/download/v1.0.0/actionbase-linux-amd64`

After creating a release, verify that the installation script works correctly.
