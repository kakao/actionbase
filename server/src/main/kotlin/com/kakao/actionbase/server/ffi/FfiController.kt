package com.kakao.actionbase.server.ffi

import com.kakao.actionbase.v2.engine.ffi.MathOps

import java.nio.file.Path

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import jakarta.annotation.PreDestroy

@RestController
@ConditionalOnProperty(name = ["ffi.enabled"], havingValue = "true", matchIfMissing = false)
class FfiController(
    @Value("\${ffi.library.path}") libraryPath: String,
) {
    private val mathOps: MathOps = MathOps.load(Path.of(libraryPath).toAbsolutePath())

    @GetMapping("/ffi/add")
    fun add(
        @RequestParam a: Int,
        @RequestParam b: Int,
    ): Int = mathOps.add(a, b)

    @PreDestroy
    fun cleanup() {
        mathOps.close()
    }
}
