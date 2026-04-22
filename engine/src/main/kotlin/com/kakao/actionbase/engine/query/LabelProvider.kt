package com.kakao.actionbase.engine.query

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label

interface LabelProvider {
    fun getLabel(name: EntityName): Label

    fun getLabel(
        service: String,
        label: String,
    ): Label = getLabel(EntityName(service, label))
}
