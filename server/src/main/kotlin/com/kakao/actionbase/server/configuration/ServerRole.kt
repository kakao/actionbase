package com.kakao.actionbase.server.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * One binary, two roles, deployed as separate processes. A control instance carries storage drivers
 * it never opens, the price of not shipping a second artifact.
 */
enum class ServerRole {
    /** Query, DML and DDL against this instance's own cluster. The default. */
    DATA,

    /** Operational APIs spanning clusters: topology, orchestration, audit. */
    CONTROL,
}

/** Creates beans only on an instance deployed as [ServerRole.CONTROL] (`actionbase.role=CONTROL`). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "actionbase",
    name = ["role"],
    havingValue = "CONTROL",
    matchIfMissing = false,
)
annotation class ConditionalOnControlRole
