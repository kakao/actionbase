#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SLATEDB_DIR="$SCRIPT_DIR/slatedb"
LIB_DIR="$SCRIPT_DIR/lib"

# Branch containing slatedb-java (PR #1253)
# TODO: Change to 'main' after PR is merged
SLATEDB_BRANCH="java-bindings"
SLATEDB_REPO="https://github.com/criccomini/slatedb.git"

# Check for rustup (needed for nightly)
if ! command -v rustup &> /dev/null; then
  echo "Error: rustup is required (slatedb uses nightly Rust features)"
  echo ""
  echo "Install rustup:"
  echo "  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
  echo ""
  echo "Then install nightly:"
  echo "  rustup install nightly"
  exit 1
fi

# Ensure nightly is installed
if ! rustup run nightly rustc --version &> /dev/null; then
  echo "Installing Rust nightly..."
  rustup install nightly
fi

# Clone if not exists
if [ ! -d "$SLATEDB_DIR" ]; then
  echo "Cloning slatedb from $SLATEDB_REPO (branch: $SLATEDB_BRANCH)..."
  git clone --depth 1 --branch "$SLATEDB_BRANCH" "$SLATEDB_REPO" "$SLATEDB_DIR"
fi

# Build slatedb-c with nightly
echo "Building slatedb-c (nightly)..."
cd "$SLATEDB_DIR"
cargo +nightly build --release -p slatedb-c

# Copy native library
mkdir -p "$LIB_DIR"

OS="$(uname -s)"
case "$OS" in
  Darwin)
    cp target/release/libslatedb_c.dylib "$LIB_DIR/"
    echo "Built: $LIB_DIR/libslatedb_c.dylib"
    ;;
  Linux)
    cp target/release/libslatedb_c.so "$LIB_DIR/"
    echo "Built: $LIB_DIR/libslatedb_c.so"
    ;;
  *)
    echo "Unsupported OS: $OS"
    exit 1
    ;;
esac

# Build slatedb-java
if [ -d "$SLATEDB_DIR/slatedb-java" ]; then
  echo "Building slatedb-java..."
  cd "$SLATEDB_DIR/slatedb-java"
  ./gradlew jar --quiet

  # Copy JAR to lib directory
  JAR_FILE=$(find build/libs -name "slatedb-*.jar" -not -name "*-sources*" -not -name "*-javadoc*" | head -1)
  if [ -n "$JAR_FILE" ]; then
    cp "$JAR_FILE" "$LIB_DIR/slatedb.jar"
    echo "Built: $LIB_DIR/slatedb.jar"
  else
    echo "Warning: slatedb-java JAR not found"
  fi
else
  echo "Warning: slatedb-java directory not found, skipping Java bindings"
fi

echo "Done."
