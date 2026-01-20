#!/bin/bash
set -e

cd "$(dirname "$0")/../.."

if [[ "$1" != "-f" ]]; then
  git diff --quiet || { echo "error: uncommitted changes (use -f to force)"; exit 1; }
fi

IMAGE=ghcr.io/kakao/actionbase
TAG=$(git rev-parse --short HEAD)
BIN_DIR=docker/standalone/build

echo "Build Plan (local):"
echo "  1. Build server JAR (gradlew :server:bootJar)"
echo "  2. Build CLI binaries (linux/amd64, linux/arm64)"
echo "  3. Build & push Docker image"
echo ""
echo "  Image: $IMAGE:standalone"
echo "  Tag:   $IMAGE:standalone-$TAG"
echo ""
read -p "Proceed? [y/N] " -n 1 -r
echo ""

[[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

echo ""
echo "=== Step 1: Build server JAR ==="
./gradlew :server:bootJar -x test --no-daemon

echo ""
echo "=== Step 2: Build CLI binaries ==="
mkdir -p "$BIN_DIR"

cd cli
VERSION=$(git describe --tags --always --match "cli/*" 2>/dev/null | sed 's|cli/||' || echo "standalone")
echo "  Version: $VERSION"

echo "  Building linux/amd64..."
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags "-s -w -X main.Version=${VERSION}" -o "../$BIN_DIR/actionbase-amd64" ./cmd/actionbase

echo "  Building linux/arm64..."
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags "-s -w -X main.Version=${VERSION}" -o "../$BIN_DIR/actionbase-arm64" ./cmd/actionbase
cd ..

echo ""
echo "=== Step 3: Build & push Docker image ==="
docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/standalone/Dockerfile \
  -t $IMAGE:standalone \
  -t $IMAGE:standalone-$TAG \
  --push .

echo ""
echo "Done: $IMAGE:standalone, $IMAGE:standalone-$TAG"
