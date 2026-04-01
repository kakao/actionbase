package com.kakao.actionbase.server.test

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

object EndpointScanner {
    private val MAPPING_ANNOTATIONS: Map<Class<out Annotation>, (Annotation) -> Pair<String, Array<String>>> =
        mapOf(
            GetMapping::class.java to { "GET" to (it as GetMapping).value },
            PostMapping::class.java to { "POST" to (it as PostMapping).value },
            PutMapping::class.java to { "PUT" to (it as PutMapping).value },
            DeleteMapping::class.java to { "DELETE" to (it as DeleteMapping).value },
            PatchMapping::class.java to { "PATCH" to (it as PatchMapping).value },
        )

    fun scan(basePackage: String): List<Pair<String, String>> {
        val provider = ClassPathScanningCandidateComponentProvider(false)
        provider.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))

        return provider
            .findCandidateComponents(basePackage)
            .flatMap { beanDef ->
                val cls = Class.forName(beanDef.beanClassName)
                val prefixes = cls.getAnnotation(RequestMapping::class.java)?.value?.ifEmpty { arrayOf("") } ?: arrayOf("")
                prefixes.flatMap { prefix ->
                    cls.declaredMethods.flatMap { m ->
                        MAPPING_ANNOTATIONS.flatMap { (annCls, extract) ->
                            m.getAnnotation(annCls)?.let { ann ->
                                val (httpMethod, paths) = extract(ann)
                                (paths.ifEmpty { arrayOf("") }).map { httpMethod to (prefix + it) }
                            } ?: emptyList()
                        }
                    }
                }
            }.distinct()
            .sortedWith(compareBy({ it.second }, { it.first }))
    }
}
