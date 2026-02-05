package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.v2.metadata.common.V2Identifier
import com.kakao.actionbase.core.v2.metadata.common.V2Index
import com.kakao.actionbase.core.v2.metadata.common.V2MutationMode
import com.kakao.actionbase.core.v2.metadata.common.V2Schema

import com.fasterxml.jackson.annotation.JsonIgnore

data class V2LabelDescriptor(
    override val active: Boolean,
    val name: String,
    override val desc: String,
    val type: String,
    val schema: V2Schema,
    val dirType: DirectionType,
    val storage: String,
    val indices: List<V2Index>,
    val groups: List<Group>,
    val event: Boolean,
    val readOnly: Boolean,
    val mode: V2MutationMode,
) : V2Descriptor<V2LabelId> {
    @JsonIgnore
    override val id: V2LabelId =
        V2Identifier.Companion.of(name).let {
            V2LabelId(
                service = it.service,
                label = it.name,
            )
        }

    // fun toV3(
    //     tenant: String,
    //     storage: StorageDescriptor,
    // ): EdgeTableDescriptor = TODO()
}
