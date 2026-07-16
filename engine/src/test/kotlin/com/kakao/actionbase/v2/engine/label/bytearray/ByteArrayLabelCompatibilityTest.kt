package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.AbstractLabelCompatibilityTest

/**
 * Runs the shared label compatibility suite against the in-memory [ByteArrayStore]-backed labels.
 * The labels are instantiated directly on a fresh store, without a Graph or table provisioning.
 */
class ByteArrayLabelCompatibilityTest : AbstractLabelCompatibilityTest() {
    override fun hashLabel(): AbstractLabel<*> = ByteArrayHashLabel(hashEntity, coder, ByteArrayStore())

    override fun indexedLabel(): AbstractLabel<*> = ByteArrayIndexedLabel.create(indexedEntity, coder, ByteArrayStore())
}
