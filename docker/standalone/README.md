# Actionbase Standalone for Quick Start

```bash
docker run -it ghcr.io/kakao/actionbase:standalone
```

## What's Included

- Server (in-memory storage)
- CLI (interactive interface)

## Build

```bash
./docker/standalone/build.sh
```

Supports linux/amd64 and linux/arm64.

## Note

Currently requires local build. Planned:
- Docker multi-stage build
- GitHub Actions for automated publishing
