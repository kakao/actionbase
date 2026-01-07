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

### 1. Prepare Release

#### Create Version Tag

Create a version tag before creating a release on GitHub:

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

#### Build Binaries for All Platforms

```bash
make build-all
```

This command generates binaries for the following platforms:
- `build/actionbase-linux-amd64` - Linux AMD64
- `build/actionbase-darwin-amd64` - macOS Intel
- `build/actionbase-darwin-arm64` - macOS Apple Silicon
- `build/actionbase-windows-amd64.exe` - Windows AMD64

### 2. Create GitHub Release

1. Go to the [GitHub Releases page](https://github.com/kakao/actionbase/releases)
2. Click "Draft a new release"
3. Select the tag (e.g., `v1.0.0`)
4. Write the release title and description
5. Upload the built binary files:
   - `actionbase-linux-amd64`
   - `actionbase-darwin-amd64`
   - `actionbase-darwin-arm64`
   - `actionbase-windows-amd64.exe`
6. Click "Publish release"

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
