package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.AbstractLabelCompatibilityTest

/**
 * Runs the shared label compatibility suite against the in-memory [ByteArrayStore]-backed labels.
 * The labels are instantiated directly on a fresh store, without a Graph or table provisioning.
 */
class ByteArrayLabelCompatibilityTest : AbstractLabelCompatibilityTest() {
    override fun hashLabel(): AbstractLabel<*> {
        val entity =
            LabelEntity(
                active = true,
                name = EntityName("test", "hash"),
                desc = "hash label",
                type = LabelType.HASH,
                schema = schema,
                dirType = DirectionType.OUT,
                storage = "mock",
            )
        return ByteArrayHashLabel(entity, coder, ByteArrayStore())
    }

    override fun indexedLabel(): AbstractLabel<*> {
        val entity =
            LabelEntity(
                active = true,
                name = EntityName("test", "indexed"),
                desc = "indexed label",
                type = LabelType.INDEXED,
                schema = schema,
                dirType = DirectionType.BOTH,
                storage = "mock",
                indices = indices,
            )
        return ByteArrayIndexedLabel.create(entity, coder, ByteArrayStore())
    }
}
