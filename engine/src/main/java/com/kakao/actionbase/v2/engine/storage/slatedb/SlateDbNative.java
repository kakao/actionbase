package com.kakao.actionbase.v2.engine.storage.slatedb;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal FFI wrapper for SlateDB C bindings.
 * <p>
 * Uses Java 22+ Foreign Function & Memory API (JEP 454).
 * Only exposes the essential operations: open, get, put, delete, close.
 */
public class SlateDbNative implements AutoCloseable {

    // CSdbValue: { data: *mut u8, len: usize }
    private static final MemoryLayout C_SDB_VALUE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("data"),
            ValueLayout.JAVA_LONG.withName("len")
    );

    // CSdbResult: { error: i32(enum), padding: i32, message: *const c_char }
    private static final MemoryLayout C_SDB_RESULT_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            ValueLayout.JAVA_INT.withName("padding"),
            ValueLayout.ADDRESS.withName("message")
    );

    // CSdbHandle: { _0: *mut SlateDbFFI }
    private static final MemoryLayout C_SDB_HANDLE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("_0")
    );

    // CSdbHandleResult: { handle: CSdbHandle, result: CSdbResult }
    private static final MemoryLayout C_SDB_HANDLE_RESULT_LAYOUT = MemoryLayout.structLayout(
            C_SDB_HANDLE_LAYOUT.withName("handle"),
            C_SDB_RESULT_LAYOUT.withName("result")
    );

    // CSdbKeyValue: { key: CSdbValue, value: CSdbValue }
    private static final MemoryLayout C_SDB_KEY_VALUE_LAYOUT = MemoryLayout.structLayout(
            C_SDB_VALUE_LAYOUT.withName("key"),
            C_SDB_VALUE_LAYOUT.withName("value")
    );

    // CSdbIteratorNextResult: { kv: CSdbKeyValue, has_value: bool(u8), padding, result: CSdbResult }
    private static final MemoryLayout C_SDB_ITERATOR_NEXT_RESULT_LAYOUT = MemoryLayout.structLayout(
            C_SDB_KEY_VALUE_LAYOUT.withName("kv"),
            ValueLayout.JAVA_BYTE.withName("has_value"),
            MemoryLayout.paddingLayout(7),
            C_SDB_RESULT_LAYOUT.withName("result")
    );

    private final Arena arena;
    private final MemorySegment dbHandle;
    private final MethodHandle getHandle;
    private final MethodHandle putHandle;
    private final MethodHandle deleteHandle;
    private final MethodHandle flushHandle;
    private final MethodHandle closeHandle;
    private final MethodHandle freeValueHandle;
    private final MethodHandle scanPrefixHandle;
    private final MethodHandle iteratorNextHandle;
    private final MethodHandle iteratorCloseHandle;

    private SlateDbNative(
            Arena arena,
            MemorySegment dbHandle,
            MethodHandle getHandle,
            MethodHandle putHandle,
            MethodHandle deleteHandle,
            MethodHandle flushHandle,
            MethodHandle closeHandle,
            MethodHandle freeValueHandle,
            MethodHandle scanPrefixHandle,
            MethodHandle iteratorNextHandle,
            MethodHandle iteratorCloseHandle
    ) {
        this.arena = arena;
        this.dbHandle = dbHandle;
        this.getHandle = getHandle;
        this.putHandle = putHandle;
        this.deleteHandle = deleteHandle;
        this.flushHandle = flushHandle;
        this.closeHandle = closeHandle;
        this.freeValueHandle = freeValueHandle;
        this.scanPrefixHandle = scanPrefixHandle;
        this.iteratorNextHandle = iteratorNextHandle;
        this.iteratorCloseHandle = iteratorCloseHandle;
    }

    private static final int ERROR_NOT_FOUND = 2;

    public byte[] get(byte[] key) throws Throwable {
        MemorySegment keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, key);
        MemorySegment valueOut = arena.allocate(C_SDB_VALUE_LAYOUT);

        MemorySegment resultSegment = (MemorySegment) getHandle.invokeExact(
                (SegmentAllocator) arena,
                dbHandle,
                keySegment,
                (long) key.length,
                MemorySegment.NULL,
                valueOut
        );

        // NotFound is not an error, just return null
        int errorCode = resultSegment.get(ValueLayout.JAVA_INT, 0);
        if (errorCode == ERROR_NOT_FOUND) {
            return null;
        }
        checkResult(resultSegment);

        MemorySegment dataPtr = valueOut.get(ValueLayout.ADDRESS, 0);
        if (dataPtr.equals(MemorySegment.NULL)) {
            return null;
        }

        int len = (int) valueOut.get(ValueLayout.JAVA_LONG, 8);
        if (len == 0) {
            return new byte[0];
        }

        byte[] bytes = new byte[len];
        dataPtr.reinterpret(len).asByteBuffer().get(bytes);

        freeValueHandle.invokeExact(valueOut);

        return bytes;
    }

    public void put(byte[] key, byte[] value) throws Throwable {
        MemorySegment keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, key);
        MemorySegment valSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, value);

        MemorySegment resultSegment = (MemorySegment) putHandle.invokeExact(
                (SegmentAllocator) arena,
                dbHandle,
                keySegment,
                (long) key.length,
                valSegment,
                (long) value.length,
                MemorySegment.NULL,
                MemorySegment.NULL
        );

        checkResult(resultSegment);
    }

    public void delete(byte[] key) throws Throwable {
        MemorySegment keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, key);

        MemorySegment resultSegment = (MemorySegment) deleteHandle.invokeExact(
                (SegmentAllocator) arena,
                dbHandle,
                keySegment,
                (long) key.length,
                MemorySegment.NULL
        );

        checkResult(resultSegment);
    }

    public void flush() throws Throwable {
        MemorySegment resultSegment = (MemorySegment) flushHandle.invokeExact(
                (SegmentAllocator) arena,
                dbHandle
        );
        checkResult(resultSegment);
    }

    /**
     * Scan keys with given prefix and return up to limit key-value pairs.
     */
    public List<Map.Entry<byte[], byte[]>> scanPrefix(byte[] prefix, int limit) throws Throwable {
        List<Map.Entry<byte[], byte[]>> results = new ArrayList<>();

        // Create iterator
        MemorySegment prefixSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, prefix);
        MemorySegment iteratorPtrOut = arena.allocate(ValueLayout.ADDRESS);

        MemorySegment resultSegment = (MemorySegment) scanPrefixHandle.invokeExact(
                (SegmentAllocator) arena,
                dbHandle,
                prefixSegment,
                (long) prefix.length,
                MemorySegment.NULL,  // scan_options
                iteratorPtrOut
        );
        checkResult(resultSegment);

        MemorySegment iteratorPtr = iteratorPtrOut.get(ValueLayout.ADDRESS, 0);
        if (iteratorPtr.equals(MemorySegment.NULL)) {
            return results;
        }

        try {
            // Iterate up to limit
            for (int i = 0; i < limit; i++) {
                MemorySegment nextResult = (MemorySegment) iteratorNextHandle.invokeExact(
                        (SegmentAllocator) arena,
                        iteratorPtr
                );

                // Check if there's a value (has_value at offset 32: 2 CSdbValue = 32 bytes)
                byte hasValue = nextResult.get(ValueLayout.JAVA_BYTE, 32);
                if (hasValue == 0) {
                    break;  // No more values
                }

                // Check for errors (result at offset 40: 32 + 1 + 7 padding = 40)
                int errorCode = nextResult.get(ValueLayout.JAVA_INT, 40);
                if (errorCode != 0) {
                    break;  // Error or end of iteration
                }

                // Extract key (CSdbValue at offset 0)
                MemorySegment keyDataPtr = nextResult.get(ValueLayout.ADDRESS, 0);
                int keyLen = (int) nextResult.get(ValueLayout.JAVA_LONG, 8);

                // Extract value (CSdbValue at offset 16)
                MemorySegment valueDataPtr = nextResult.get(ValueLayout.ADDRESS, 16);
                int valueLen = (int) nextResult.get(ValueLayout.JAVA_LONG, 24);

                if (!keyDataPtr.equals(MemorySegment.NULL) && keyLen > 0) {
                    byte[] keyBytes = new byte[keyLen];
                    keyDataPtr.reinterpret(keyLen).asByteBuffer().get(keyBytes);

                    byte[] valueBytes = new byte[valueLen];
                    if (!valueDataPtr.equals(MemorySegment.NULL) && valueLen > 0) {
                        valueDataPtr.reinterpret(valueLen).asByteBuffer().get(valueBytes);
                    }

                    results.add(new AbstractMap.SimpleEntry<>(keyBytes, valueBytes));
                }
            }
        } finally {
            // Close iterator
            MemorySegment closeResult = (MemorySegment) iteratorCloseHandle.invokeExact(
                    (SegmentAllocator) arena,
                    iteratorPtr
            );
            // Ignore close errors
        }

        return results;
    }

    @Override
    public void close() throws Exception {
        try {
            MemorySegment resultSegment = (MemorySegment) closeHandle.invokeExact(
                    (SegmentAllocator) arena,
                    dbHandle
            );
            checkResult(resultSegment);
        } catch (Throwable e) {
            throw new SlateDbException(-1, "Failed to close database: " + e.getMessage());
        } finally {
            arena.close();
        }
    }

    private void checkResult(MemorySegment resultSegment) throws SlateDbException {
        int errorCode = resultSegment.get(ValueLayout.JAVA_INT, 0);
        if (errorCode != 0) {
            MemorySegment msgPtr = resultSegment.get(ValueLayout.ADDRESS, 8);
            String msg;
            if (!msgPtr.equals(MemorySegment.NULL)) {
                msg = msgPtr.reinterpret(1024).getString(0);
            } else {
                msg = "Unknown error";
            }
            throw new SlateDbException(errorCode, msg);
        }
    }

    /**
     * Open database with object store URL.
     * For local filesystem, use "file:///path/to/storage" as the url.
     */
    public static SlateDbNative open(String dbPath, String url, Path libraryPath) throws Throwable {
        Arena arena = Arena.ofShared();
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        Linker linker = Linker.nativeLinker();

        // slatedb_open(path, url, env_file) -> CSdbHandleResult
        MethodHandle openHandle = linker.downcallHandle(
                lookup.find("slatedb_open").orElseThrow(() -> new IllegalStateException("Function 'slatedb_open' not found")),
                FunctionDescriptor.of(
                        C_SDB_HANDLE_RESULT_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        MemorySegment pathSegment = arena.allocateFrom(dbPath);
        MemorySegment urlSegment = url != null ? arena.allocateFrom(url) : MemorySegment.NULL;
        MemorySegment handleResultSegment = (MemorySegment) openHandle.invokeExact(
                (SegmentAllocator) arena,
                pathSegment,
                urlSegment,
                MemorySegment.NULL
        );

        int errorCode = handleResultSegment.get(ValueLayout.JAVA_INT, 8);
        if (errorCode != 0) {
            MemorySegment msgPtr = handleResultSegment.get(ValueLayout.ADDRESS, 16);
            String msg;
            if (!msgPtr.equals(MemorySegment.NULL)) {
                msg = msgPtr.reinterpret(1024).getString(0);
            } else {
                msg = "Failed to open database";
            }
            arena.close();
            throw new SlateDbException(errorCode, msg);
        }

        MemorySegment dbHandle = handleResultSegment.asSlice(0, 8);
        return createInstance(arena, dbHandle, lookup, linker);
    }

    private static SlateDbNative createInstance(Arena arena, MemorySegment dbHandle, SymbolLookup lookup, Linker linker) {
        // slatedb_get_with_options
        MethodHandle getHandle = linker.downcallHandle(
                lookup.find("slatedb_get_with_options").orElseThrow(() -> new IllegalStateException("Function 'slatedb_get_with_options' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        // slatedb_put_with_options
        MethodHandle putHandle = linker.downcallHandle(
                lookup.find("slatedb_put_with_options").orElseThrow(() -> new IllegalStateException("Function 'slatedb_put_with_options' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        // slatedb_delete_with_options
        MethodHandle deleteHandle = linker.downcallHandle(
                lookup.find("slatedb_delete_with_options").orElseThrow(() -> new IllegalStateException("Function 'slatedb_delete_with_options' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS
                )
        );

        // slatedb_flush
        MethodHandle flushHandle = linker.downcallHandle(
                lookup.find("slatedb_flush").orElseThrow(() -> new IllegalStateException("Function 'slatedb_flush' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT
                )
        );

        // slatedb_close
        MethodHandle closeHandle = linker.downcallHandle(
                lookup.find("slatedb_close").orElseThrow(() -> new IllegalStateException("Function 'slatedb_close' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT
                )
        );

        // slatedb_free_value
        MethodHandle freeValueHandle = linker.downcallHandle(
                lookup.find("slatedb_free_value").orElseThrow(() -> new IllegalStateException("Function 'slatedb_free_value' not found")),
                FunctionDescriptor.ofVoid(C_SDB_VALUE_LAYOUT)
        );

        // slatedb_scan_prefix_with_options(handle, prefix, prefix_len, scan_options, iterator_ptr) -> CSdbResult
        MethodHandle scanPrefixHandle = linker.downcallHandle(
                lookup.find("slatedb_scan_prefix_with_options").orElseThrow(() -> new IllegalStateException("Function 'slatedb_scan_prefix_with_options' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        C_SDB_HANDLE_LAYOUT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        // slatedb_iterator_next(iter) -> CSdbIteratorNextResult
        MethodHandle iteratorNextHandle = linker.downcallHandle(
                lookup.find("slatedb_iterator_next").orElseThrow(() -> new IllegalStateException("Function 'slatedb_iterator_next' not found")),
                FunctionDescriptor.of(
                        C_SDB_ITERATOR_NEXT_RESULT_LAYOUT,
                        ValueLayout.ADDRESS
                )
        );

        // slatedb_iterator_close(iter) -> CSdbResult
        MethodHandle iteratorCloseHandle = linker.downcallHandle(
                lookup.find("slatedb_iterator_close").orElseThrow(() -> new IllegalStateException("Function 'slatedb_iterator_close' not found")),
                FunctionDescriptor.of(
                        C_SDB_RESULT_LAYOUT,
                        ValueLayout.ADDRESS
                )
        );

        return new SlateDbNative(arena, dbHandle, getHandle, putHandle, deleteHandle, flushHandle, closeHandle, freeValueHandle, scanPrefixHandle, iteratorNextHandle, iteratorCloseHandle);
    }
}
