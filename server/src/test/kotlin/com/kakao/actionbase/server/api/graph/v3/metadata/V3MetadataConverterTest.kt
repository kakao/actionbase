package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order as V3Order
import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.code.hbase.Order as V2Order
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.CacheField
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toAliasResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toDatabaseResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toLabelType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toTableResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toTableType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2AliasEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2ServiceEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3MutationMode
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested

class V3MetadataConverterTest {
    private fun labelEntity(
        database: String,
        table: String,
        comment: String = "test table",
        type: LabelType = LabelType.INDEXED,
        schema: EdgeSchema =
            EdgeSchema(
                VertexField(VertexType.STRING, "source"),
                VertexField(VertexType.STRING, "target"),
                listOf(V2Field("score", DataType.INT, true, "score field")),
            ),
        dirType: V2DirectionType = V2DirectionType.OUT,
        storage: String = "datastore://test_namespace/test_table",
        indices: List<V2Index> = emptyList(),
        caches: List<Cache> = emptyList(),
        readOnly: Boolean = false,
        mode: V2MutationMode = V2MutationMode.SYNC,
    ): LabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(database, table),
            desc = comment,
            type = type,
            schema = schema,
            dirType = dirType,
            storage = storage,
            indices = indices,
            groups = emptyList(),
            caches = caches,
            event = false,
            readOnly = readOnly,
            mode = mode,
        )

    @Nested
    inner class DatabaseConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              active: true
              comment: test database
            """,
        )
        fun `ServiceEntity to DatabaseResponse`(
            database: String,
            active: Boolean,
            comment: String,
        ) {
            val v2Entity =
                ServiceEntity(
                    active = active,
                    name = EntityName.fromOrigin(database),
                    desc = comment,
                )
            val response = v2Entity.toDatabaseResponse()
            assertThat(response.database).isEqualTo(database)
            assertThat(response.active).isEqualTo(active)
            assertThat(response.comment).isEqualTo(comment)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              active: true
              comment: test database
            """,
        )
        fun `DatabaseResponse to ServiceEntity`(
            database: String,
            active: Boolean,
            comment: String,
        ) {
            val response =
                DatabaseResponse(
                    active = active,
                    database = database,
                    comment = comment,
                )
            val v2Entity = response.toV2ServiceEntity()
            assertThat(v2Entity.name.nameNotNull).isEqualTo(database)
            assertThat(v2Entity.active).isEqualTo(active)
            assertThat(v2Entity.desc).isEqualTo(comment)
        }
    }

    @Nested
    inner class MutationModeConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v2: SYNC
              v3: SYNC
            - v2: ASYNC
              v3: ASYNC
            - v2: IGNORE
              v3: DROP
            """,
        )
        fun `V2 to V3 MutationMode`(
            v2: String,
            v3: String,
        ) {
            assertThat(V2MutationMode.valueOf(v2).toV3MutationMode())
                .isEqualTo(MutationMode.valueOf(v3))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v3: SYNC
              v2: SYNC
            - v3: ASYNC
              v2: ASYNC
            - v3: DROP
              v2: IGNORE
            """,
        )
        fun `V3 to V2 MutationMode`(
            v3: String,
            v2: String,
        ) {
            assertThat(MutationMode.valueOf(v3).toV2MutationMode())
                .isEqualTo(V2MutationMode.valueOf(v2))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - mode: DENY
            """,
        )
        fun `V3 DENY throws exception`(mode: String) {
            assertThatThrownBy { MutationMode.valueOf(mode).toV2MutationMode() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("DENY is not supported")
        }
    }

    @Nested
    inner class DirectionTypeConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v2: BOTH
              v3: BOTH
            - v2: OUT
              v3: OUT
            - v2: IN
              v3: IN
            """,
        )
        fun `V2 to V3 DirectionType`(
            v2: String,
            v3: String,
        ) {
            assertThat(V2DirectionType.valueOf(v2).toV3DirectionType())
                .isEqualTo(DirectionType.valueOf(v3))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v3: BOTH
              v2: BOTH
            - v3: OUT
              v2: OUT
            - v3: IN
              v2: IN
            """,
        )
        fun `V3 to V2 DirectionType`(
            v3: String,
            v2: String,
        ) {
            assertThat(DirectionType.valueOf(v3).toV2DirectionType())
                .isEqualTo(V2DirectionType.valueOf(v2))
        }
    }

    @Nested
    inner class TableTypeConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v2: INDEXED
              v3: EDGE
            - v2: IMMUTABLE_INDEXED
              v3: IMMUTABLE_EDGE
            - v2: MULTI_EDGE
              v3: MULTI_EDGE
            - v2: VERTEX
              v3: VERTEX
            """,
        )
        fun `V2 LabelType to V3 TableType`(
            v2: String,
            v3: String,
        ) {
            assertThat(LabelType.valueOf(v2).toTableType()).isEqualTo(TableType.valueOf(v3))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v3: EDGE
              v2: INDEXED
            - v3: IMMUTABLE_EDGE
              v2: IMMUTABLE_INDEXED
            - v3: MULTI_EDGE
              v2: MULTI_EDGE
            - v3: VERTEX
              v2: VERTEX
            """,
        )
        fun `V3 TableType to V2 LabelType`(
            v3: String,
            v2: String,
        ) {
            assertThat(TableType.valueOf(v3).toLabelType()).isEqualTo(LabelType.valueOf(v2))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - v2: HASH
            - v2: NIL
            """,
        )
        fun `V2 only LabelType reads back as EDGE`(v2: String) {
            assertThat(LabelType.valueOf(v2).toTableType()).isEqualTo(TableType.EDGE)
        }
    }

    @Nested
    inner class TableConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              table: mytable
              labelType: INDEXED
              tableType: EDGE
              sourceType: STRING
              sourceComment: source
              targetType: STRING
              targetComment: target
              direction: OUT
              mode: SYNC
              storage: "datastore://test_namespace/test_table"
              comment: test table
            """,
        )
        fun `LabelEntity to TableResponse`(
            database: String,
            table: String,
            labelType: String,
            tableType: String,
            sourceType: String,
            sourceComment: String,
            targetType: String,
            targetComment: String,
            direction: String,
            mode: String,
            storage: String,
            comment: String,
        ) {
            val v2Entity =
                labelEntity(
                    database = database,
                    table = table,
                    comment = comment,
                    type = LabelType.valueOf(labelType),
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.valueOf(sourceType), sourceComment),
                            VertexField(VertexType.valueOf(targetType), targetComment),
                            listOf(V2Field("score", DataType.INT, true, "score field")),
                        ),
                    dirType = V2DirectionType.valueOf(direction),
                    storage = storage,
                    indices = listOf(V2Index("idx1", listOf(V2Index.Field("score", V2Order.DESC)), "index desc")),
                    mode = V2MutationMode.valueOf(mode),
                )

            val response = v2Entity.toTableResponse()
            assertThat(response.database).isEqualTo(database)
            assertThat(response.table).isEqualTo(table)
            assertThat(response.active).isTrue()
            assertThat(response.comment).isEqualTo(comment)
            assertThat(response.type).isEqualTo(TableType.valueOf(tableType))
            assertThat(response.mode).isEqualTo(MutationMode.valueOf(mode))
            assertThat(response.storage).isEqualTo(storage)
            assertThat(response.direction).isEqualTo(DirectionType.valueOf(direction))

            assertThat(response.schema.source.type).isEqualTo(VertexType.valueOf(sourceType))
            assertThat(response.schema.source.comment).isEqualTo(sourceComment)
            assertThat(response.schema.target.type).isEqualTo(VertexType.valueOf(targetType))
            assertThat(response.schema.properties).hasSize(1)
            assertThat(response.schema.properties[0].name).isEqualTo("score")
            assertThat(response.schema.properties[0].type).isEqualTo(DataType.INT)
            assertThat(response.schema.properties[0].nullable).isTrue()

            assertThat(response.indexes).hasSize(1)
            assertThat(response.indexes[0].index).isEqualTo("idx1")
            assertThat(response.indexes[0].fields[0].field).isEqualTo("score")
            assertThat(response.indexes[0].fields[0].order).isEqualTo(V3Order.DESC)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              table: mymultiedge
            """,
        )
        fun `MultiEdge keeps its id as the _id property, exactly as V2 stores it`(
            database: String,
            table: String,
        ) {
            val v2Entity =
                labelEntity(
                    database = database,
                    table = table,
                    type = LabelType.MULTI_EDGE,
                    readOnly = true,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.LONG, "sender"),
                            VertexField(VertexType.LONG, "receiver"),
                            listOf(
                                V2Field("_id", DataType.LONG, false, "order id"),
                                V2Field("amount", DataType.INT, true, "amount"),
                            ),
                        ),
                )

            val response = v2Entity.toTableResponse()
            assertThat(response.type).isEqualTo(TableType.MULTI_EDGE)
            assertThat(response.schema.properties.map { it.name }).containsExactly("_id", "amount")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              table: users
              idType: STRING
              idComment: user id
            - database: mydb
              table: users_long
              idType: LONG
              idComment: numeric user id
            """,
        )
        fun `Vertex keeps its id in source, exactly as V2 stores it`(
            database: String,
            table: String,
            idType: String,
            idComment: String,
        ) {
            val v2Entity =
                labelEntity(
                    database = database,
                    table = table,
                    type = LabelType.VERTEX,
                    dirType = V2DirectionType.OUT,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.valueOf(idType), idComment),
                            VertexField(VertexType.STRING, "<vertex>"),
                            listOf(V2Field("name", DataType.STRING, false, "user name")),
                        ),
                )

            val response = v2Entity.toTableResponse()
            assertThat(response.type).isEqualTo(TableType.VERTEX)
            assertThat(response.schema.source.type).isEqualTo(VertexType.valueOf(idType))
            assertThat(response.schema.source.comment).isEqualTo(idComment)
            assertThat(response.schema.target.comment).isEqualTo("<vertex>")
            assertThat(response.schema.properties.map { it.name }).containsExactly("name")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              table: mytable
              cacheName: cache1
              cacheLimit: 50
              cacheComment: cache desc
            """,
        )
        fun `caches pass through untouched`(
            database: String,
            table: String,
            cacheName: String,
            cacheLimit: Int,
            cacheComment: String,
        ) {
            val caches =
                listOf(
                    Cache(
                        cache = cacheName,
                        fields = listOf(CacheField("score", V3Order.DESC)),
                        limit = cacheLimit,
                        comment = cacheComment,
                    ),
                )
            val v2Entity = labelEntity(database = database, table = table, caches = caches)

            assertThat(v2Entity.toTableResponse().caches).isEqualTo(caches)
        }
    }

    @Nested
    inner class AliasConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              alias: myalias
              table: mytable
              active: true
              comment: test alias
            """,
        )
        fun `AliasEntity to AliasResponse`(
            database: String,
            alias: String,
            table: String,
            active: Boolean,
            comment: String,
        ) {
            val v2Entity =
                AliasEntity(
                    active = active,
                    name = EntityName(database, alias),
                    desc = comment,
                    target = EntityName(database, table),
                )
            val response = v2Entity.toAliasResponse()
            assertThat(response.database).isEqualTo(database)
            assertThat(response.alias).isEqualTo(alias)
            assertThat(response.table).isEqualTo(table)
            assertThat(response.active).isEqualTo(active)
            assertThat(response.comment).isEqualTo(comment)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              alias: myalias
              table: mytable
              active: true
              comment: test alias
            """,
        )
        fun `AliasResponse to AliasEntity`(
            database: String,
            alias: String,
            table: String,
            active: Boolean,
            comment: String,
        ) {
            val response =
                AliasResponse(
                    active = active,
                    database = database,
                    alias = alias,
                    table = table,
                    comment = comment,
                )
            val v2Entity = response.toV2AliasEntity()
            assertThat(v2Entity.name.service).isEqualTo(database)
            assertThat(v2Entity.name.nameNotNull).isEqualTo(alias)
            assertThat(v2Entity.target.service).isEqualTo(database)
            assertThat(v2Entity.target.nameNotNull).isEqualTo(table)
            assertThat(v2Entity.active).isEqualTo(active)
            assertThat(v2Entity.desc).isEqualTo(comment)
        }
    }
}
