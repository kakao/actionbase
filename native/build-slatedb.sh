#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SLATEDB_DIR="$SCRIPT_DIR/slatedb"
LIB_DIR="$SCRIPT_DIR/lib"

# Branch containing slatedb-java (PR #1253)
# TODO: Change to 'main' after PR is merged
SLATEDB_BRANCH="java-bindings"
SLATEDB_REPO="https://github.com/criccomini/slatedb.git"

# Parse arguments
BUILD_RUST=true
BUILD_JAVA=true
RUN_TESTS=false

for arg in "$@"; do
  case $arg in
    --java-only)
      BUILD_RUST=false
      ;;
    --rust-only)
      BUILD_JAVA=false
      ;;
    --test)
      RUN_TESTS=true
      ;;
  esac
done

# Determine native library name
OS="$(uname -s)"
case "$OS" in
  Darwin) NATIVE_LIB="libslatedb_c.dylib" ;;
  Linux)  NATIVE_LIB="libslatedb_c.so" ;;
  *)      NATIVE_LIB="" ;;
esac

# Skip Rust build if native library already exists
if [ "$BUILD_RUST" = true ] && [ -f "$LIB_DIR/$NATIVE_LIB" ]; then
  echo "Native library already exists: $LIB_DIR/$NATIVE_LIB"
  echo "Skipping Rust build (use 'rm $LIB_DIR/$NATIVE_LIB' to force rebuild)"
  BUILD_RUST=false
fi

# Check for rustup only if we need to build Rust
if [ "$BUILD_RUST" = true ]; then
  if ! command -v rustup &> /dev/null; then
    echo "Error: rustup is required for building slatedb-c"
    echo ""
    echo "Install rustup:"
    echo "  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    echo ""
    echo "Or run with --java-only to skip Rust build (requires native lib to exist)"
    exit 1
  fi

  # Ensure nightly is installed (slatedb uses nightly features)
  if ! rustup run nightly rustc --version &> /dev/null; then
    echo "Installing Rust nightly..."
    rustup install nightly
  fi
fi

# Clone if not exists
if [ ! -d "$SLATEDB_DIR" ]; then
  echo "Cloning slatedb from $SLATEDB_REPO (branch: $SLATEDB_BRANCH)..."
  git clone --depth 1 --branch "$SLATEDB_BRANCH" "$SLATEDB_REPO" "$SLATEDB_DIR"
fi

mkdir -p "$LIB_DIR"

# Build slatedb-c
if [ "$BUILD_RUST" = true ]; then
  echo "Building slatedb-c..."
  cd "$SLATEDB_DIR"
  cargo +nightly build --release -p slatedb-c

  cp "target/release/$NATIVE_LIB" "$LIB_DIR/"
  echo "Built: $LIB_DIR/$NATIVE_LIB"
fi

# Build slatedb-java
if [ "$BUILD_JAVA" = true ]; then
  if [ -d "$SLATEDB_DIR/slatedb-java" ]; then
    echo "Building slatedb-java..."
    cd "$SLATEDB_DIR/slatedb-java"

    # Set SLATEDB_C_LIB for tests (as per upstream CI)
    export SLATEDB_C_LIB="$LIB_DIR/$NATIVE_LIB"

    if [ "$RUN_TESTS" = true ]; then
      echo "Running slatedb-java tests..."
      ./gradlew check
    else
      ./gradlew jar --quiet
    fi

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
fi

echo "Done."
