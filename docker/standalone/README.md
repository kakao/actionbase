# Actionbase Standalone for Quick Start

```bash
docker run -it ghcr.io/kakao/actionbase:standalone
```

## What's Included

- Server (in-memory storage)
- CLI (interactive interface)

## Local Build & Run

```bash
./docker/standalone/build-and-run.sh
```

## CI

GitHub Actions workflow: `.github/workflows/standalone-image.yml`

Triggers:
- Push tag `standalone/*` (e.g., `standalone/v1.0.0`)
- Manual dispatch

Platforms: linux/amd64, linux/arm64

## Note

Planned:
- Docker multi-stage build (no local build dependency)
