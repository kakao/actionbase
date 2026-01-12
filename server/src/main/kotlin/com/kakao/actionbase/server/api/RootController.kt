package com.kakao.actionbase.server.api

import java.time.Clock
import java.time.Instant

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class RootController(
    @Autowired(required = false) private val buildProperties: BuildProperties?,
) {
    @GetMapping("/")
    fun root(): Mono<ResponseEntity<Map<String, String>>> {
        val version = buildProperties?.version ?: "unknown"
        val timestamp = Instant.now(Clock.systemUTC()).toString()

        val response =
            mapOf(
                "status" to "UP",
                "message" to "Actionbase is running",
                "version" to version,
                "timestamp" to timestamp,
            )

        return Mono.just(ResponseEntity.ok(response))
    }
}
