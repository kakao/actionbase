package com.kakao.actionbase.test.documentations.params

@Retention
@Target(AnnotationTarget.FUNCTION)
annotation class ObjectSource(
    val value: String = "",
    val cases: String = "",
    val shared: String = "",
)
