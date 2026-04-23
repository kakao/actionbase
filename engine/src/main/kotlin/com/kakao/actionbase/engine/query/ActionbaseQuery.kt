package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.engine.sql.StatKey

import java.util.UUID

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
        JsonSubTypes.Type(value = Item.Seek::class, name = "CACHE"),
    )
    sealed class Item {
        abstract val name: String
        abstract val include: Boolean
        abstract val memoize: Boolean
        abstract val post: List<PostProcessor>
        abstract val aggregators: List<Aggregator>

        data class Self(
            override val name: String = UUID.randomUUID().toString(),
            val database: String,
            val table: String,
            val source: Vertex,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
            override val aggregators: List<Aggregator> = emptyList(),
        ) : Item()

        data class Get(
            override val name: String = UUID.randomUUID().toString(),
            val database: String,
            val table: String,
            val source: Vertex,
            val target: Vertex,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
            override val aggregators: List<Aggregator> = emptyList(),
        ) : Item()

        data class Count(
            override val name: String = UUID.randomUUID().toString(),
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
            override val aggregators: List<Aggregator> = emptyList(),
        ) : Item()

        data class Scan(
            override val name: String = UUID.randomUUID().toString(),
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            val index: String,
            val limit: Int = DEFAULT_SCAN_LIMIT,
            val offset: String? = null,
            val ranges: String? = null,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
            override val aggregators: List<Aggregator> = emptyList(),
        ) : Item()

        data class Seek(
            override val name: String = UUID.randomUUID().toString(),
            val database: String,
            val table: String,
            val source: Vertex,
            val direction: Direction,
            val cache: String,
            val limit: Int,
            override val include: Boolean = false,
            override val memoize: Boolean = false,
            override val post: List<PostProcessor> = emptyList(),
            override val aggregators: List<Aggregator> = emptyList(),
        ) : Item()
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Vertex.Value::class, name = "VALUE"),
        JsonSubTypes.Type(value = Vertex.Ref::class, name = "REF"),
        JsonSubTypes.Type(value = Vertex.Step::class, name = "STEP"),
    )
    sealed class Vertex {
        data class Value(
            val value: List<Any>,
        ) : Vertex()

        data class Ref(
            val ref: String,
            val field: String,
        ) : Vertex()

        data class Step(
            val step: Item,
            val field: String,
        ) : Vertex()
    }

    companion object {
        private const val DEFAULT_SCAN_LIMIT = 10

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

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Aggregator.Flatten::class, name = "FLATTEN"),
        JsonSubTypes.Type(value = Aggregator.Count::class, name = "COUNT"),
        JsonSubTypes.Type(value = Aggregator.Sum::class, name = "SUM"),
    )
    sealed class Aggregator {
        data object Flatten : Aggregator()

        data class Count(
            val field: String,
            val order: Order,
            val limit: Int,
        ) : Aggregator()

        data class Sum(
            val valueField: String,
            val keyFields: List<String>,
            val order: Order,
            val limit: Int,
        ) : Aggregator()
    }
}
