#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SLATEDB_DIR="$SCRIPT_DIR/slatedb"
LIB_DIR="$SCRIPT_DIR/lib"

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
  echo "Cloning slatedb..."
  git clone --depth 1 https://github.com/slatedb/slatedb.git "$SLATEDB_DIR"
fi

# Build slatedb-c with nightly
echo "Building slatedb-c (nightly)..."
cd "$SLATEDB_DIR"
cargo +nightly build --release -p slatedb-c

# Copy library
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

echo "Done."
