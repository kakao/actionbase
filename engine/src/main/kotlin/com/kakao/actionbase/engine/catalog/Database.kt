package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.DatabaseDescriptor

interface Database {
    val descriptor: DatabaseDescriptor
}
