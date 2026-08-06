package com.kakao.actionbase.server.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * What an instance is deployed to be.
 *
 * One binary, two roles, deployed as separate processes with different config. A control instance
 * carries the storage drivers it never opens, which is the price of not maintaining a second
 * artifact, image, and release.
 */
enum class ServerRole {
    /** Query, DML and DDL against this instance's own cluster. The default. */
    DATA,

    /** Operational APIs spanning clusters: topology, orchestration, audit. */
    CONTROL,
}

/**
 * Creates beans only on an instance deployed as [ServerRole.CONTROL] (`actionbase.role=CONTROL`).
 * Data-plane endpoints stay on either way; keeping a control instance from serving traffic is a
 * routing decision, not this annotation's job.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "actionbase",
    name = ["role"],
    havingValue = "CONTROL",
    matchIfMissing = false,
)
annotation class ConditionalOnControlRole
