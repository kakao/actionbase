package com.kakao.actionbase.server.configuration.resolver

import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.server.configuration.HttpHeaderConstants

import org.springframework.core.MethodParameter
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange

import reactor.core.publisher.Mono

class ServerRequestContextArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == RequestContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        bindingContext: BindingContext,
        exchange: ServerWebExchange,
    ): Mono<Any> {
        val headers = exchange.request.headers

        val requestId =
            headers.getFirst(HttpHeaderConstants.REQUEST_ID)
                ?: RequestContext.DEFAULT.requestId

        val actor =
            headers.getFirst(HttpHeaderConstants.ACTOR_ID)
                ?: headers.getFirst(HttpHeaderConstants.AB_ACTOR_ID)
                ?: RequestContext.DEFAULT.actor

        val includeContext =
            headers
                .getFirst(HttpHeaderConstants.INCLUDE_MUTATION_CONTEXT)
                ?.trim()
                ?.equals("true", ignoreCase = true) == true

        return Mono.just(RequestContext(requestId, actor, includeContext))
    }
}
