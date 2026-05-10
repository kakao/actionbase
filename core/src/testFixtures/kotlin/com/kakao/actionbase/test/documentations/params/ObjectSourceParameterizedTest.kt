package com.kakao.actionbase.test.documentations.params

import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

@Retention
@TestTemplate
@MustBeDocumented
@ExtendWith(SourceExtension::class)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class ObjectSourceParameterizedTest
