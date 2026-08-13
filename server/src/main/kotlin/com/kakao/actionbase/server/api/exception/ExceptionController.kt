package com.kakao.actionbase.server.api.exception

import com.kakao.actionbase.engine.service.NoSuchPreparedQueryException

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ServerWebInputException

import reactor.core.publisher.Mono

@ControllerAdvice
class ExceptionController {
    private val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

    data class ErrorResponse(
        val timestamp: Long,
        val status: Int,
        val error: String,
        val message: String,
    )

    fun createErrorResponse(
        exception: Exception,
        status: HttpStatus,
        logging: () -> Unit,
    ): Mono<ResponseEntity<ErrorResponse>> {
        logging()
        return Mono.just(
            ResponseEntity.status(status).body(
                ErrorResponse(
                    timestamp = System.currentTimeMillis(),
                    status = status.value(),
                    error = status.reasonPhrase,
                    message = exception.message ?: "Unknown error",
                ),
            ),
        )
    }

    @ExceptionHandler(
        IllegalStateException::class,
        ServerWebInputException::class,
        UnsupportedOperationException::class,
        IllegalArgumentException::class,
    )
    fun handleClientError(e: Exception): Mono<ResponseEntity<ErrorResponse>> = createErrorResponse(e, HttpStatus.BAD_REQUEST) { logger.error("Bad request occurred: ${e.message}", e) }

    @ExceptionHandler(NoSuchPreparedQueryException::class)
    fun handleNotFound(e: Exception): Mono<ResponseEntity<ErrorResponse>> = createErrorResponse(e, HttpStatus.NOT_FOUND) { logger.info("Not found: ${e.message}") }
}
