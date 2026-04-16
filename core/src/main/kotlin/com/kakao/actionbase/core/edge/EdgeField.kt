package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.v2.edge.V2EdgeField

object EdgeField {
    const val VERSION = "version"
    const val SOURCE = "source"
    const val TARGET = "target"
    const val DIRECTION = "direction"

    fun toV3(name: String): String =
        when (name) {
            V2EdgeField.VERSION -> VERSION
            V2EdgeField.SOURCE -> SOURCE
            V2EdgeField.TARGET -> TARGET
            V2EdgeField.DIRECTION -> DIRECTION
            else -> name
        }
}
