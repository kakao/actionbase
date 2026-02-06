package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order as V3Order
import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.code.hbase.Order as V2Order
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2AliasEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2ServiceEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3AliasDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DatabaseDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3TableDescriptor
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
    private val tenant = "test-tenant"

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
        fun `ServiceEntity to DatabaseDescriptor`(
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
            val v3Descriptor = v2Entity.toV3DatabaseDescriptor(tenant)
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo(database)
            assertThat(v3Descriptor.active).isEqualTo(active)
            assertThat(v3Descriptor.comment).isEqualTo(comment)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              active: true
              comment: test database
            """,
        )
        fun `DatabaseDescriptor to ServiceEntity`(
            database: String,
            active: Boolean,
            comment: String,
        ) {
            val v3Descriptor =
                DatabaseDescriptor(
                    tenant = tenant,
                    database = database,
                    active = active,
                    comment = comment,
                )
            val v2Entity = v3Descriptor.toV2ServiceEntity()
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
    inner class TableConversionTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - database: mydb
              table: mytable
              labelType: HASH
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
        fun `LabelEntity to TableDescriptor`(
            database: String,
            table: String,
            labelType: String,
            sourceType: String,
            sourceComment: String,
            targetType: String,
            targetComment: String,
            direction: String,
            mode: String,
            storage: String,
            comment: String,
        ) {
            val edgeSchema =
                EdgeSchema(
                    VertexField(VertexType.valueOf(sourceType), sourceComment),
                    VertexField(VertexType.valueOf(targetType), targetComment),
                    listOf(
                        com.kakao.actionbase.v2.core.types
                            .Field("score", DataType.INT, true, "score field"),
                    ),
                )
            val v2Entity =
                LabelEntity(
                    active = true,
                    name = EntityName(database, table),
                    desc = comment,
                    type = LabelType.valueOf(labelType),
                    schema = edgeSchema,
                    dirType = V2DirectionType.valueOf(direction),
                    storage = storage,
                    indices =
                        listOf(
                            V2Index("idx1", listOf(V2Index.Field("score", V2Order.DESC)), "index desc"),
                        ),
                    groups = emptyList(),
                    event = false,
                    readOnly = false,
                    mode = V2MutationMode.valueOf(mode),
                )

            val v3Descriptor = v2Entity.toV3TableDescriptor(tenant) as TableDescriptor.Edge
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo(database)
            assertThat(v3Descriptor.table).isEqualTo(table)
            assertThat(v3Descriptor.active).isTrue()
            assertThat(v3Descriptor.comment).isEqualTo(comment)
            assertThat(v3Descriptor.mode).isEqualTo(MutationMode.valueOf(mode))
            assertThat(v3Descriptor.storage).isEqualTo(storage)

            val schema = v3Descriptor.schema
            assertThat(schema.direction).isEqualTo(DirectionType.valueOf(direction))
            assertThat(schema.source.type).isEqualTo(PrimitiveType.valueOf(sourceType))
            assertThat(schema.target.type).isEqualTo(PrimitiveType.valueOf(targetType))
            assertThat(schema.properties).hasSize(1)
            assertThat(schema.properties[0].name).isEqualTo("score")
            assertThat(schema.properties[0].type).isEqualTo(PrimitiveType.INT)
            assertThat(schema.properties[0].nullable).isTrue()

            assertThat(schema.indexes).hasSize(1)
            assertThat(schema.indexes[0].index).isEqualTo("idx1")
            assertThat(schema.indexes[0].fields[0].field).isEqualTo("score")
            assertThat(schema.indexes[0].fields[0].order).isEqualTo(V3Order.DESC)
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
        fun `AliasEntity to AliasDescriptor`(
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
            val v3Descriptor = v2Entity.toV3AliasDescriptor(tenant)
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo(database)
            assertThat(v3Descriptor.alias).isEqualTo(alias)
            assertThat(v3Descriptor.table).isEqualTo(table)
            assertThat(v3Descriptor.active).isEqualTo(active)
            assertThat(v3Descriptor.comment).isEqualTo(comment)
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
        fun `AliasDescriptor to AliasEntity`(
            database: String,
            alias: String,
            table: String,
            active: Boolean,
            comment: String,
        ) {
            val v3Descriptor =
                AliasDescriptor(
                    tenant = tenant,
                    database = database,
                    alias = alias,
                    table = table,
                    active = active,
                    comment = comment,
                )
            val v2Entity = v3Descriptor.toV2AliasEntity()
            assertThat(v2Entity.name.service).isEqualTo(database)
            assertThat(v2Entity.name.nameNotNull).isEqualTo(alias)
            assertThat(v2Entity.target.service).isEqualTo(database)
            assertThat(v2Entity.target.nameNotNull).isEqualTo(table)
            assertThat(v2Entity.active).isEqualTo(active)
            assertThat(v2Entity.desc).isEqualTo(comment)
        }
    }
}
