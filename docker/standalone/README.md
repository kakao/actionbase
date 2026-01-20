# Actionbase Standalone for Quick Start

```bash
docker run -it ghcr.io/kakao/actionbase:standalone
```

## What's Included

- Server (in-memory storage)
- CLI (interactive interface)

## CI

GitHub Actions workflow: `.github/workflows/standalone-image.yml`

Triggers:
- Push to `main` (when docker/standalone, server, or cli changes)
- Manual dispatch

Platforms: linux/amd64, linux/arm64

## Note

Planned:
- Docker multi-stage build (no local build dependency)
