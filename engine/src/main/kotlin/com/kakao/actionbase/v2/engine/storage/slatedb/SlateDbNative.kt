package com.kakao.actionbase.v2.engine.storage.slatedb

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path

/**
 * Minimal FFI wrapper for SlateDB C bindings.
 *
 * Uses Java 22+ Foreign Function & Memory API (JEP 454).
 * Only exposes the essential operations: open, get, put, delete, close.
 */
class SlateDbNative private constructor(
    private val arena: Arena,
    private val dbHandle: MemorySegment,
    private val getHandle: MethodHandle,
    private val putHandle: MethodHandle,
    private val deleteHandle: MethodHandle,
    private val closeHandle: MethodHandle,
) : AutoCloseable {

    fun get(key: ByteArray): ByteArray? {
        val keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, *key)
        val keyValue = allocateCValue(keySegment, key.size)
        val errorSegment = allocateError()

        val resultSegment = getHandle.invokeExact(
            dbHandle,
            keyValue,
            MemorySegment.NULL, // default read options
            errorSegment,
        ) as MemorySegment

        checkError(errorSegment)
        return extractBytes(resultSegment)
    }

    fun put(key: ByteArray, value: ByteArray) {
        val keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, *key)
        val valSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, *value)
        val keyValue = allocateCValue(keySegment, key.size)
        val valValue = allocateCValue(valSegment, value.size)
        val errorSegment = allocateError()

        putHandle.invokeExact(
            dbHandle,
            keyValue,
            valValue,
            MemorySegment.NULL, // default put options
            errorSegment,
        )

        checkError(errorSegment)
    }

    fun delete(key: ByteArray) {
        val keySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, *key)
        val keyValue = allocateCValue(keySegment, key.size)
        val errorSegment = allocateError()

        deleteHandle.invokeExact(
            dbHandle,
            keyValue,
            MemorySegment.NULL, // default options
            errorSegment,
        )

        checkError(errorSegment)
    }

    override fun close() {
        closeHandle.invokeExact(dbHandle)
        arena.close()
    }

    private fun allocateCValue(data: MemorySegment, len: Int): MemorySegment {
        val cValue = arena.allocate(C_SDB_VALUE_LAYOUT)
        cValue.set(ValueLayout.ADDRESS, 0, data)
        cValue.set(ValueLayout.JAVA_LONG, 8, len.toLong())
        return cValue
    }

    private fun allocateError(): MemorySegment {
        return arena.allocate(C_SDB_ERROR_LAYOUT)
    }

    private fun checkError(errorSegment: MemorySegment) {
        val code = errorSegment.get(ValueLayout.JAVA_INT, 0)
        if (code != 0) {
            val msgPtr = errorSegment.get(ValueLayout.ADDRESS, 8)
            val msg = if (msgPtr != MemorySegment.NULL) {
                msgPtr.reinterpret(256).getString(0)
            } else {
                "Unknown error"
            }
            throw SlateDbException(code, msg)
        }
    }

    private fun extractBytes(cValue: MemorySegment): ByteArray? {
        val ptr = cValue.get(ValueLayout.ADDRESS, 0)
        if (ptr == MemorySegment.NULL) return null

        val len = cValue.get(ValueLayout.JAVA_LONG, 8).toInt()
        if (len == 0) return ByteArray(0)

        val bytes = ByteArray(len)
        ptr.reinterpret(len.toLong()).asByteBuffer().get(bytes)
        return bytes
    }

    companion object {
        // CSdbValue: { ptr: *const u8, len: usize }
        private val C_SDB_VALUE_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("ptr"),
            ValueLayout.JAVA_LONG.withName("len"),
        )

        // CSdbError: { code: i32, padding: i32, message: *const c_char }
        private val C_SDB_ERROR_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("code"),
            ValueLayout.JAVA_INT.withName("padding"),
            ValueLayout.ADDRESS.withName("message"),
        )

        fun open(dbPath: String, libraryPath: Path): SlateDbNative {
            val arena = Arena.ofShared()
            val lookup = SymbolLookup.libraryLookup(libraryPath, arena)
            val linker = Linker.nativeLinker()

            // slatedb_open(path: *const c_char, error: *mut CSdbError) -> *mut CSdbHandle
            val openHandle = linker.downcallHandle(
                lookup.find("slatedb_open").orElseThrow { noFunction("slatedb_open") },
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, // return: handle
                    ValueLayout.ADDRESS, // path
                    ValueLayout.ADDRESS, // error
                ),
            )

            val pathSegment = arena.allocateFrom(dbPath)
            val errorSegment = arena.allocate(C_SDB_ERROR_LAYOUT)

            val dbHandle = openHandle.invokeExact(pathSegment, errorSegment) as MemorySegment

            val errorCode = errorSegment.get(ValueLayout.JAVA_INT, 0)
            if (errorCode != 0 || dbHandle == MemorySegment.NULL) {
                arena.close()
                throw SlateDbException(errorCode, "Failed to open database at $dbPath")
            }

            // Bind remaining functions
            val getHandle = linker.downcallHandle(
                lookup.find("slatedb_get_with_options").orElseThrow { noFunction("slatedb_get_with_options") },
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, // return: CSdbValue
                    ValueLayout.ADDRESS, // db handle
                    ValueLayout.ADDRESS, // key (CSdbValue - passed by value as struct)
                    ValueLayout.ADDRESS, // options
                    ValueLayout.ADDRESS, // error
                ),
            )

            val putHandle = linker.downcallHandle(
                lookup.find("slatedb_put_with_options").orElseThrow { noFunction("slatedb_put_with_options") },
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // db handle
                    ValueLayout.ADDRESS, // key
                    ValueLayout.ADDRESS, // value
                    ValueLayout.ADDRESS, // options
                    ValueLayout.ADDRESS, // error
                ),
            )

            val deleteHandle = linker.downcallHandle(
                lookup.find("slatedb_delete_with_options").orElseThrow { noFunction("slatedb_delete_with_options") },
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // db handle
                    ValueLayout.ADDRESS, // key
                    ValueLayout.ADDRESS, // options
                    ValueLayout.ADDRESS, // error
                ),
            )

            val closeHandle = linker.downcallHandle(
                lookup.find("slatedb_close").orElseThrow { noFunction("slatedb_close") },
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
            )

            return SlateDbNative(arena, dbHandle, getHandle, putHandle, deleteHandle, closeHandle)
        }

        private fun noFunction(name: String) = IllegalStateException("Function '$name' not found in library")
    }
}

class SlateDbException(val code: Int, message: String) : RuntimeException("SlateDB error ($code): $message")
