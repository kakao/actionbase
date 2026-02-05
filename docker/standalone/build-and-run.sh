#!/bin/bash
set -e

cd "$(dirname "$0")/../.."

IMAGE=ghcr.io/kakao/actionbase
TAG=$(git rev-parse --short HEAD)
git diff --quiet || TAG="$TAG-dirty"
ARCH=$(uname -m)
[[ "$ARCH" == "x86_64" ]] && ARCH="amd64"
[[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]] && ARCH="arm64"

echo "Build Plan (local):"
echo "  1. Build server JAR"
echo "  2. Build CLI binary ($ARCH)"
echo "  3. Build Docker image"
echo "  4. Run"
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
echo "=== Step 2: Build CLI binary ==="
mkdir -p docker/standalone/build

cd cli
VERSION=$(git describe --tags --always --match "cli/*" 2>/dev/null | sed 's|cli/||' || echo "standalone")
echo "  Version: $VERSION"
echo "  Building linux/$ARCH..."
CGO_ENABLED=0 GOOS=linux GOARCH=$ARCH go build -ldflags "-s -w -X main.Version=${VERSION}" -o "../docker/standalone/build/actionbase-$ARCH" ./cmd/actionbase
cd ..

echo ""
echo "=== Step 3: Build Docker image ==="
docker build \
  -f docker/standalone/Dockerfile \
  -t $IMAGE:standalone \
  -t $IMAGE:standalone-$TAG \
  --build-arg TARGETARCH=$ARCH \
  .

echo ""
echo "=== Step 4: Run ==="
docker run -it --rm $IMAGE:standalone
