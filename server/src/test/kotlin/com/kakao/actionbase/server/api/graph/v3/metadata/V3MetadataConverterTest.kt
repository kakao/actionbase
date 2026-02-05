package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order as V3Order
import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.code.hbase.Order as V2Order
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.Storage
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2AliasEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2ServiceEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2StorageString
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3AliasDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DatabaseDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3Storage
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3TableDescriptor
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class V3MetadataConverterTest {
    private val tenant = "test-tenant"

    @Nested
    inner class DatabaseConversionTest {
        @Test
        fun `ServiceEntity to DatabaseDescriptor`() {
            // Given
            val v2Entity =
                ServiceEntity(
                    active = true,
                    name = EntityName.fromOrigin("mydb"),
                    desc = "test database",
                )

            // When
            val v3Descriptor = v2Entity.toV3DatabaseDescriptor(tenant)

            // Then
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo("mydb")
            assertThat(v3Descriptor.active).isTrue()
            assertThat(v3Descriptor.comment).isEqualTo("test database")
        }

        @Test
        fun `DatabaseDescriptor to ServiceEntity`() {
            // Given
            val v3Descriptor =
                DatabaseDescriptor(
                    tenant = tenant,
                    database = "mydb",
                    active = true,
                    comment = "test database",
                )

            // When
            val v2Entity = v3Descriptor.toV2ServiceEntity()

            // Then
            assertThat(v2Entity.name.nameNotNull).isEqualTo("mydb")
            assertThat(v2Entity.active).isTrue()
            assertThat(v2Entity.desc).isEqualTo("test database")
        }
    }

    @Nested
    inner class MutationModeConversionTest {
        @ParameterizedTest
        @CsvSource(
            "SYNC, SYNC",
            "ASYNC, ASYNC",
            "IGNORE, DROP",
        )
        fun `V2 to V3 MutationMode`(
            v2Mode: V2MutationMode,
            expectedV3Mode: MutationMode,
        ) {
            assertThat(v2Mode.toV3MutationMode()).isEqualTo(expectedV3Mode)
        }

        @ParameterizedTest
        @CsvSource(
            "SYNC, SYNC",
            "ASYNC, ASYNC",
            "DROP, IGNORE",
        )
        fun `V3 to V2 MutationMode`(
            v3Mode: MutationMode,
            expectedV2Mode: V2MutationMode,
        ) {
            assertThat(v3Mode.toV2MutationMode()).isEqualTo(expectedV2Mode)
        }

        @Test
        fun `V3 DENY throws exception`() {
            assertThatThrownBy { MutationMode.DENY.toV2MutationMode() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("DENY is not supported")
        }
    }

    @Nested
    inner class DirectionTypeConversionTest {
        @ParameterizedTest
        @CsvSource(
            "BOTH, BOTH",
            "OUT, OUT",
            "IN, IN",
        )
        fun `V2 to V3 DirectionType`(
            v2Dir: V2DirectionType,
            expectedV3Dir: DirectionType,
        ) {
            assertThat(v2Dir.toV3DirectionType()).isEqualTo(expectedV3Dir)
        }

        @ParameterizedTest
        @CsvSource(
            "BOTH, BOTH",
            "OUT, OUT",
            "IN, IN",
        )
        fun `V3 to V2 DirectionType`(
            v3Dir: DirectionType,
            expectedV2Dir: V2DirectionType,
        ) {
            assertThat(v3Dir.toV2DirectionType()).isEqualTo(expectedV2Dir)
        }
    }

    @Nested
    inner class StorageConversionTest {
        @Test
        fun `datastore URI to V3 Storage`() {
            // Given
            val uri = "datastore://hbase/my-table"

            // When
            val storage = uri.toV3Storage()

            // Then
            assertThat(storage).isInstanceOf(Storage.HBase::class.java)
            assertThat((storage as Storage.HBase).tableName).isEqualTo("my-table")
        }

        @Test
        fun `V3 Storage to datastore URI`() {
            // Given
            val storage = Storage.HBase(tableName = "my-table")

            // When
            val uri = storage.toV2StorageString()

            // Then
            assertThat(uri).isEqualTo("datastore://hbase/my-table")
        }

        @Test
        fun `non-datastore URI throws exception`() {
            val invalidUri = "hbase:my-table"

            assertThatThrownBy { invalidUri.toV3Storage() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("datastore://")
        }

        @Test
        fun `empty HBase table name throws exception`() {
            val emptyTableUri = "datastore://hbase/"

            assertThatThrownBy { emptyTableUri.toV3Storage() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("HBase table name is required")
        }

        @Test
        fun `datastore URI without table name throws exception`() {
            val noTableUri = "datastore://hbase"

            assertThatThrownBy { noTableUri.toV3Storage() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("HBase table name is required")
        }
    }

    @Nested
    inner class TableConversionTest {
        @Test
        fun `LabelEntity to TableDescriptor`() {
            // Given
            val edgeSchema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "source"),
                    VertexField(VertexType.STRING, "target"),
                    listOf(
                        com.kakao.actionbase.v2.core.types
                            .Field("score", DataType.INT, true, "score field"),
                    ),
                )

            val v2Entity =
                LabelEntity(
                    active = true,
                    name = EntityName("mydb", "mytable"),
                    desc = "test table",
                    type = LabelType.HASH,
                    schema = edgeSchema,
                    dirType = V2DirectionType.OUT,
                    storage = "datastore://hbase/test-table",
                    indices =
                        listOf(
                            V2Index("idx1", listOf(V2Index.Field("score", V2Order.DESC)), "index desc"),
                        ),
                    groups = emptyList(),
                    event = false,
                    readOnly = false,
                    mode = V2MutationMode.SYNC,
                )

            // When
            val v3Descriptor = v2Entity.toV3TableDescriptor(tenant)

            // Then
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo("mydb")
            assertThat(v3Descriptor.table).isEqualTo("mytable")
            assertThat(v3Descriptor.active).isTrue()
            assertThat(v3Descriptor.comment).isEqualTo("test table")
            assertThat(v3Descriptor.mode).isEqualTo(MutationMode.SYNC)
            assertThat(v3Descriptor.storage).isEqualTo(Storage.HBase("test-table"))

            val schema = v3Descriptor.schema
            assertThat(schema.direction).isEqualTo(DirectionType.OUT)
            assertThat(schema.source.type).isEqualTo(PrimitiveType.STRING)
            assertThat(schema.target.type).isEqualTo(PrimitiveType.STRING)
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
        @Test
        fun `AliasEntity to AliasDescriptor`() {
            // Given
            val v2Entity =
                AliasEntity(
                    active = true,
                    name = EntityName("mydb", "myalias"),
                    desc = "test alias",
                    target = EntityName("mydb", "mytable"),
                )

            // When
            val v3Descriptor = v2Entity.toV3AliasDescriptor(tenant)

            // Then
            assertThat(v3Descriptor.tenant).isEqualTo(tenant)
            assertThat(v3Descriptor.database).isEqualTo("mydb")
            assertThat(v3Descriptor.alias).isEqualTo("myalias")
            assertThat(v3Descriptor.table).isEqualTo("mytable")
            assertThat(v3Descriptor.active).isTrue()
            assertThat(v3Descriptor.comment).isEqualTo("test alias")
        }

        @Test
        fun `AliasDescriptor to AliasEntity`() {
            // Given
            val v3Descriptor =
                AliasDescriptor(
                    tenant = tenant,
                    database = "mydb",
                    alias = "myalias",
                    table = "mytable",
                    active = true,
                    comment = "test alias",
                )

            // When
            val v2Entity = v3Descriptor.toV2AliasEntity()

            // Then
            assertThat(v2Entity.name.service).isEqualTo("mydb")
            assertThat(v2Entity.name.nameNotNull).isEqualTo("myalias")
            assertThat(v2Entity.target.service).isEqualTo("mydb")
            assertThat(v2Entity.target.nameNotNull).isEqualTo("mytable")
            assertThat(v2Entity.active).isTrue()
            assertThat(v2Entity.desc).isEqualTo("test alias")
        }
    }
}
