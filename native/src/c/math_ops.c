/**
 * Simple C library for FFI demonstration.
 *
 * Compile on macOS:
 *   clang -shared -o libmathops.dylib math_ops.c
 *
 * Compile on Linux:
 *   gcc -shared -fPIC -o libmathops.so math_ops.c
 */

int add(int a, int b) {
  return a + b;
}
