package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity
import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest as V2AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasUpdateRequest as V2AliasUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest as V2LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest as V2LabelUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest as V2ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest as V2ServiceUpdateRequest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.ParameterizedTypeReference

/**
 * V2-V3 API Compatibility E2E Tests
 *
 * Tests that V2 and V3 APIs are fully compatible:
 * - Create with V2 → Update with V3 → Verify consistency
 * - Create with V3 → Update with V2 → Verify consistency
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2V3CompatibilityTest : E2ETestBase() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DatabaseCompatibilityTest {
        private val db = "compat-db-test"

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            // Create with V2 API
            client.post()
                .uri("/graph/v2/service/$db")
                .bodyValue(V2ServiceCreateRequest(desc = "created by v2"))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<ServiceEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("created by v2") }

            // Update with V3 API
            client.put()
                .uri("/graph/v3/databases/$db")
                .bodyValue(DatabaseUpdateRequest(comment = "updated by v3"))
                .exchange()
                .expectStatus().isOk
                .expectBody(DatabaseDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db")
                .exchange()
                .expectStatus().isOk
                .expectBody(ServiceEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v3") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db")
                .exchange()
                .expectStatus().isOk
                .expectBody(DatabaseDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val db2 = "compat-db-test-2"

            // Create with V3 API
            client.post()
                .uri("/graph/v3/databases/$db2")
                .bodyValue(DatabaseCreateRequest(db2, "created by v3"))
                .exchange()
                .expectStatus().isOk
                .expectBody(DatabaseDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("created by v3") }

            // Update with V2 API
            client.put()
                .uri("/graph/v2/service/$db2")
                .bodyValue(V2ServiceUpdateRequest(active = true, desc = "updated by v2"))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<ServiceEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("updated by v2") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db2")
                .exchange()
                .expectStatus().isOk
                .expectBody(ServiceEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v2") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db2")
                .exchange()
                .expectStatus().isOk
                .expectBody(DatabaseDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v2") }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "compat-table-db"
        private val table = "compat-table"

        @BeforeAll
        fun setup() {
            // Create database first
            client.post()
                .uri("/graph/v3/databases/$db")
                .bodyValue(DatabaseCreateRequest(db, "test db"))
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            // Create with V2 API (label)
            client.post()
                .uri("/graph/v2/service/$db/label/$table")
                .bodyValue(v2LabelCreateRequest("created by v2"))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<LabelEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("created by v2") }

            // Update with V3 API (table)
            client.put()
                .uri("/graph/v3/databases/$db/tables/$table")
                .bodyValue(TableUpdateRequest(comment = "updated by v3"))
                .exchange()
                .expectStatus().isOk
                .expectBody(TableDescriptor.Edge::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/$table")
                .exchange()
                .expectStatus().isOk
                .expectBody(LabelEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v3") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/$table")
                .exchange()
                .expectStatus().isOk
                .expectBody(TableDescriptor.Edge::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val table2 = "compat-table-2"

            // Create with V3 API (table)
            client.post()
                .uri("/graph/v3/databases/$db/tables/$table2")
                .bodyValue(v3TableCreateRequest("created by v3"))
                .exchange()
                .expectStatus().isOk
                .expectBody(TableDescriptor.Edge::class.java)
                .value { assertThat(it.comment).isEqualTo("created by v3") }

            // Update with V2 API (label)
            client.put()
                .uri("/graph/v2/service/$db/label/$table2")
                .bodyValue(V2LabelUpdateRequest(
                    active = true,
                    desc = "updated by v2",
                    type = null,
                    schema = null,
                    groups = null,
                    indices = null,
                    readOnly = null,
                    mode = null,
                ))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<LabelEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("updated by v2") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/$table2")
                .exchange()
                .expectStatus().isOk
                .expectBody(LabelEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v2") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/$table2")
                .exchange()
                .expectStatus().isOk
                .expectBody(TableDescriptor.Edge::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v2") }
        }

        private fun v2LabelCreateRequest(desc: String) = V2LabelCreateRequest(
            desc = desc,
            type = LabelType.HASH,
            schema = EdgeSchema(
                VertexField(VertexType.STRING, "source"),
                VertexField(VertexType.STRING, "target"),
                emptyList(),
            ),
            dirType = V2DirectionType.OUT,
            storage = "datastore://hbase/compat-table-storage",
        )

        private fun v3TableCreateRequest(comment: String) = TableCreateRequest(
            schema = ModelSchema.Edge(
                source = Field(PrimitiveType.STRING, "source"),
                target = Field(PrimitiveType.STRING, "target"),
                properties = emptyList(),
                direction = DirectionType.OUT,
            ),
            storage = "datastore://hbase/compat-table2-storage",
            mode = MutationMode.SYNC,
            comment = comment,
        )
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "compat-alias-db"
        private val table = "compat-alias-target"
        private val alias = "compat-alias"

        @BeforeAll
        fun setup() {
            // Create database
            client.post()
                .uri("/graph/v3/databases/$db")
                .bodyValue(DatabaseCreateRequest(db, "test db"))
                .exchange()
                .expectStatus().isOk

            // Create table (alias target)
            client.post()
                .uri("/graph/v3/databases/$db/tables/$table")
                .bodyValue(TableCreateRequest(
                    schema = ModelSchema.Edge(
                        source = Field(PrimitiveType.STRING, "src"),
                        target = Field(PrimitiveType.STRING, "tgt"),
                        properties = emptyList(),
                        direction = DirectionType.OUT,
                    ),
                    storage = "datastore://hbase/compat-alias-target-storage",
                    mode = MutationMode.SYNC,
                    comment = "target table",
                ))
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            // Create with V2 API
            client.post()
                .uri("/graph/v2/service/$db/alias/$alias")
                .bodyValue(V2AliasCreateRequest(
                    desc = "created by v2",
                    target = "$db.$table",
                ))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<AliasEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("created by v2") }

            // Update with V3 API
            client.put()
                .uri("/graph/v3/databases/$db/aliases/$alias")
                .bodyValue(AliasUpdateRequest(comment = "updated by v3"))
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/$alias")
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v3") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$alias")
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v3") }
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val alias2 = "compat-alias-2"

            // Create with V3 API
            client.post()
                .uri("/graph/v3/databases/$db/aliases/$alias2")
                .bodyValue(AliasCreateRequest(
                    table = table,
                    comment = "created by v3",
                ))
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("created by v3") }

            // Update with V2 API
            client.put()
                .uri("/graph/v2/service/$db/alias/$alias2")
                .bodyValue(V2AliasUpdateRequest(
                    active = true,
                    desc = "updated by v2",
                    target = null,
                ))
                .exchange()
                .expectStatus().isOk
                .expectBody(object : ParameterizedTypeReference<DdlStatus<AliasEntity>>() {})
                .value { assertThat(it.result?.desc).isEqualTo("updated by v2") }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/$alias2")
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasEntity::class.java)
                .value { assertThat(it.desc).isEqualTo("updated by v2") }

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$alias2")
                .exchange()
                .expectStatus().isOk
                .expectBody(AliasDescriptor::class.java)
                .value { assertThat(it.comment).isEqualTo("updated by v2") }
        }
    }
}
