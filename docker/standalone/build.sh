#!/bin/bash
set -e

cd "$(dirname "$0")/../.."

if [[ "$1" != "-f" ]]; then
  git diff --quiet || { echo "error: uncommitted changes (use -f to force)"; exit 1; }
fi

TAG=$(git rev-parse --short HEAD)
IMAGE=ghcr.io/kakao/actionbase

echo "Build Plan:"
echo "  Image:     $IMAGE:standalone"
echo "  Tag:       $IMAGE:standalone-$TAG"
echo "  Platforms: linux/amd64, linux/arm64"
echo ""
read -p "Proceed? [y/N] " -n 1 -r
echo ""

[[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/standalone/Dockerfile \
  -t $IMAGE:standalone \
  -t $IMAGE:standalone-$TAG \
  --push .

echo "Done: $IMAGE:standalone, $IMAGE:standalone-$TAG"
