package com.kakao.actionbase.engine.query

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.WherePredicate

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class ActionbaseQuery(
    val query: List<Item>,
    val stats: Set<StatKey> = emptySet(),
) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Item.Self::class, name = "SELF"),
        JsonSubTypes.Type(value = Item.Get::class, name = "GET"),
        JsonSubTypes.Type(value = Item.Count::class, name = "COUNT"),
        JsonSubTypes.Type(value = Item.Scan::class, name = "SCAN"),
        JsonSubTypes.Type(value = Item.Cache::class, name = "CACHE"),
    )
    sealed class Item {
        abstract val name: String
        abstract val include: Boolean
        abstract val memoize: Boolean
        abstract val post: List<PostProcessor>

        data class Self(
            override val name: String,
            val database: String,
            val table: String,
            val source: Vertex,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
        ) : Item()

        data class Get(
            override val name: String,
            val database: String,
            val table: String,
            val source: Vertex,
            val target: Vertex,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
        ) : Item()

        data class Count(
            override val name: String,
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
        ) : Item()

        data class Scan(
            override val name: String,
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            val index: String,
            val limit: Int,
            val offset: String? = null,
            val predicates: List<WherePredicate>? = null,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
        ) : Item()

        data class Cache(
            override val name: String,
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            val cache: String,
            val limit: Int,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
        ) : Item()
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Vertex.Value::class, name = "VALUE"),
        JsonSubTypes.Type(value = Vertex.Ref::class, name = "REF"),
    )
    sealed class Vertex {
        data class Value(
            val value: List<Any>,
        ) : Vertex()

        data class Ref(
            val ref: String,
            val field: String,
        ) : Vertex()
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun from(json: String): ActionbaseQuery = objectMapper.readValue(json)

        fun ActionbaseQuery.toJson(): String = objectMapper.writeValueAsString(this)
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = PostProcessor.JsonObject::class, name = "JSON_OBJECT"),
        JsonSubTypes.Type(value = PostProcessor.SplitExplode::class, name = "SPLIT_EXPLODE"),
    )
    sealed class PostProcessor {
        data class JsonObject(
            val field: String,
            val paths: List<Path>,
            val drop: Boolean = true,
        ) : PostProcessor() {
            data class Path(
                val path: String,
                val alias: String,
                val dataType: DataType,
            )
        }

        data class SplitExplode(
            val field: String,
            val regex: String,
            val limit: Int = 0,
            val alias: String,
            val dataType: DataType,
            val drop: Boolean = true,
        ) : PostProcessor()
    }
}
