#!/usr/bin/env bash
set -euo pipefail

REPO="kakao/actionbase"
BINARY="actionbase"
INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
TAG="cli"
VERSION="${VERSION:-v0.0.1}"

error() {
  echo "$1" >&2
  exit 1
}

info() {
  echo "▶ $1"
}

OS="$(uname | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64) ARCH="amd64" ;;
  arm64|aarch64) ARCH="arm64" ;;
  *)
    error "Unsupported architecture: $ARCH"
    ;;
esac

case "$OS" in
  darwin|linux) ;;
  *)
    error "Unsupported OS: $OS"
    ;;
esac

FILENAME="${BINARY}_${OS}_${ARCH}"
BASE_URL="https://github.com/$REPO/releases/download/$TAG/$VERSION"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

info "Installing $BINARY ($TAG:$VERSION)"
info "Downloading $BASE_URL"

if ! curl -fL "$BASE_URL/$FILENAME" -o "$TMP_DIR/$BINARY"; then
  echo "Failed to download: $BASE_URL/$FILENAME" >&2
  exit 1
fi

if curl -fsSL "$BASE_URL/checksums.txt" -o "$TMP_DIR/checksums.txt"; then
  info "Verifying checksum"
  (
    cd "$TMP_DIR"
    grep " $FILENAME\$" checksums.txt | shasum -a 256 -c -
  ) || error "Checksum verification failed"
else
  info "Checksum file not found, skipping verification"
fi

chmod +x "$TMP_DIR/$BINARY"

if [ -w "$INSTALL_DIR" ]; then
  mv "$TMP_DIR/$BINARY" "$INSTALL_DIR/$BINARY"
else
  sudo mv "$TMP_DIR/$BINARY" "$INSTALL_DIR/$BINARY"
fi

info "Installed successfully"
