package com.kakao.actionbase.v2.engine.ffi

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path

/**
 * Java FFI wrapper for native math operations.
 *
 * Demonstrates Java 22+ Foreign Function & Memory API (JEP 454).
 */
class MathOps private constructor(
    private val arena: Arena,
    private val addHandle: MethodHandle,
) : AutoCloseable {

    fun add(a: Int, b: Int): Int {
        return addHandle.invokeExact(a, b) as Int
    }

    override fun close() {
        arena.close()
    }

    companion object {
        fun load(libraryPath: Path): MathOps {
            val arena = Arena.ofShared()
            val lookup = SymbolLookup.libraryLookup(libraryPath, arena)
            val linker = Linker.nativeLinker()

            val descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            )

            val addHandle = linker.downcallHandle(
                lookup.find("add").orElseThrow { IllegalStateException("Function 'add' not found") },
                descriptor,
            )

            return MathOps(arena, addHandle)
        }
    }
}
