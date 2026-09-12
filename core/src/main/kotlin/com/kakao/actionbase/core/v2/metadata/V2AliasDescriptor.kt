package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.v2.metadata.common.V2Identifier

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * name: <service>.<alias>
 * target: <service>.<label>
 */
data class V2AliasDescriptor(
    val name: String,
    val target: String,
    override val desc: String,
    override val active: Boolean,
) : V2Descriptor<V2AliasId> {
    @JsonIgnore
    override val id: V2AliasId =
        V2Identifier.Companion.of(name).let {
            V2AliasId(
                service = it.service,
                alias = it.name,
            )
        }

    @JsonIgnore
    val targetLabel = V2Identifier.Companion.of(target).name
}
