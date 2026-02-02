#!/bin/bash
set -e

cd "$(dirname "$0")/src/c"

OS="$(uname -s)"
case "$OS" in
  Darwin)
    clang -shared -o libmathops.dylib math_ops.c
    echo "Built: libmathops.dylib"
    ;;
  Linux)
    gcc -shared -fPIC -o libmathops.so math_ops.c
    echo "Built: libmathops.so"
    ;;
  *)
    echo "Unsupported OS: $OS"
    exit 1
    ;;
esac
