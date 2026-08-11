package com.kakao.actionbase.server.api.graph.v3.datastore.hbase

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseAdmin
import com.kakao.actionbase.server.ServerApplication

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

import io.mockk.mockk

internal const val TEST_NAMESPACE = "ab_test"

/**
 * Stands up the HBase datastore controllers, which the default test context leaves out - it runs on
 * `actionbase.datastore.type=memory`.
 *
 * [hBaseAdmin] replaces the cluster-backed one. Note that the service builds the admin call before
 * the guard runs - `guard.then(hBaseAdmin.deleteTable(..))` evaluates its argument eagerly - so a
 * test proves a rejection by stubbing the call with a publisher that fails on subscribe, not by
 * leaving it unstubbed.
 */
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(HBaseDatastoreE2ETestBase.StubbedDatastore::class)
@SpringBootTest(
    classes = [ServerApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "actionbase.datastore.type=HBASE",
        "actionbase.datastore.configuration[actionbase.hbase.namespace]=$TEST_NAMESPACE",
    ],
)
abstract class HBaseDatastoreE2ETestBase {
    @Autowired
    protected lateinit var client: WebTestClient

    @Autowired
    protected lateinit var hBaseAdmin: HBaseAdmin

    @TestConfiguration
    class StubbedDatastore {
        @Bean
        @Primary
        fun stubHBaseAdmin(): HBaseAdmin = mockk()
    }
}
