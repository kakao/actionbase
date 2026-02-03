package com.kakao.actionbase.v2.engine.storage.slatedb

import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.storage.Storage
import com.kakao.actionbase.v2.engine.storage.Storage.Companion.parseOptions

class SlateDbStorage(
    override val entity: StorageEntity,
) : Storage<SlateDbOptions> {
    override val options: SlateDbOptions = parseOptions(entity.conf)
}
