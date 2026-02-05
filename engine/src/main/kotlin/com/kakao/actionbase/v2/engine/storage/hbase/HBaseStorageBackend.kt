package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.client.mock.MockHTable
import org.apache.hadoop.security.UserGroupInformation
import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class HBaseStorageBackend private constructor(
    private val mock: Boolean,
    val connectionMono: Mono<AsyncConnection>,
    val namespace: String,
    val config: Configuration,
) : StorageBackend {
    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val resolvedNamespace = if (namespace.isBlank()) this.namespace else namespace
        return if (mock) {
            val conn = HBaseConnections.getMockConnection(resolvedNamespace)
            val table = NewMockTable(conn.getTable(TableName.valueOf("edges")) as MockHTable)
            val bucket = HBaseStorageBucket(HBaseTable.create(table))
            Mono.just(StorageBuckets(bucket, bucket))
        } else {
            connectionMono.map { connection ->
                val table = connection.getTable(TableName.valueOf(resolvedNamespace, name))
                val bucket = HBaseStorageBucket(HBaseTable.create(table))
                StorageBuckets(bucket, bucket)
            }
        }
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> {
        val (namespace, name) = parseUri(uri)
        return getBucket(namespace, name)
    }

    override fun close() {
        connectionMono.block()?.close()
    }

    private fun parseUri(uri: String): Pair<String, String> {
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri. Expected format: datastore://{namespace}/{tableName}" }
        return parts[0] to parts[1]
    }

    companion object {
        const val DEFAULT_HBASE_NAMESPACE = "default"

        private val logger = LoggerFactory.getLogger(HBaseStorageBackend::class.java)

        fun create(properties: Map<String, String>): HBaseStorageBackend {
            val config = HBaseConfiguration.create()

            if (properties.isEmpty() || properties["version"] == "embedded") {
                logger.info("🚀 - Using Embedded Mock HBase cluster")
                return HBaseStorageBackend(
                    mock = true,
                    connectionMono = Mono.empty(),
                    namespace = DEFAULT_HBASE_NAMESPACE,
                    config = config,
                )
            }

            val isSecure = properties["secure"]?.toBoolean() ?: false
            val version = properties["version"] ?: "2.4"
            val namespace = properties["namespace"] ?: throw IllegalArgumentException("HBase namespace is not set")

            require(version.startsWith("2.4") || version.startsWith("2.5")) {
                "Unsupported HBase version: $version. Supported versions are 2.4.x and 2.5.x."
            }

            val krb5ConfPathOpt: String? = properties["krb5ConfPath"] ?: System.getenv("AB_KRB5_CONF_PATH")
            val principalOpt: String? = properties["principal"] ?: System.getenv("AB_PRINCIPAL")
            val keytabPathOpt: String? = properties["keytabPath"] ?: System.getenv("AB_KEYTAB_PATH")

            val zookeeperQuorumOpt: String? = properties["hbase.zookeeper.quorum"]
            val clientBootstrapServersOpt: String? = properties["hbase.client.bootstrap.servers"]

            if (isSecure) {
                val krb5ConfPath = krb5ConfPathOpt ?: throw IllegalStateException("Kerberos krb5.conf path is not set")
                val principal = principalOpt ?: throw IllegalStateException("Kerberos principal is not set")
                val keytabPath = keytabPathOpt ?: throw IllegalStateException("Kerberos keytab path is not set")

                System.setProperty("java.security.krb5.conf", krb5ConfPath)

                config["hadoop.security.authentication"] = "kerberos"
                config["hbase.security.authentication"] = "kerberos"
                config["hbase.master.kerberos.principal"] = "hbase/_HOST@KAKAO.HADOOP"
                config["hbase.regionserver.kerberos.principal"] = "hbase/_HOST@KAKAO.HADOOP"

                config["hbase.client.keytab.principal"] = principal
                config["hbase.client.keytab.file"] = keytabPath
            }

            if (version.startsWith("2.4")) {
                logger.info("🚀 - Using HBase 2.4 - zookeeperQuorum: $zookeeperQuorumOpt")
                config["hbase.zookeeper.quorum"] =
                    zookeeperQuorumOpt ?: throw IllegalStateException("zookeeper.quorum is not set")
            } else if (version.startsWith("2.5")) {
                logger.info("🚀 - Using HBase 2.5 - clientBootstrapServers: $clientBootstrapServersOpt")
                config["hbase.client.registry.impl"] = "org.apache.hadoop.hbase.client.RpcConnectionRegistry"
                config["hbase.client.bootstrap.servers"] =
                    clientBootstrapServersOpt ?: throw IllegalStateException("hbase.client.bootstrap.servers is not set")
            }

            properties.forEach { (key, value) ->
                if (key.startsWith("hbase.")) {
                    config[key] = value
                } else if (key.startsWith("hadoop.")) {
                    config[key] = value
                }
            }

            if (isSecure) {
                logger.info("🚀 - Using secure HBase cluster with Kerberos authentication")
                UserGroupInformation.setConfiguration(config)
            }

            val checkConnectionConfig =
                org.apache.hadoop.conf
                    .Configuration(config)
            checkConnectionConfig.setInt("zookeeper.recovery.retry", 1) // HBase 2.4 only
            checkConnectionConfig.setInt("hbase.client.retries.number", 1) // Common
            checkConnectionConfig.setInt("hbase.client.connection.registry.impl.retry", 1)
            checkConnectionConfig.setInt("hbase.client.registry.timeout", 10000)
            checkConnectionConfig.setInt("hbase.client.operation.timeout", 10000)
            checkConnectionConfig.setInt("hbase.rpc.timeout", 10000)

            val connectionMono =
                Mono
                    .fromFuture(ConnectionFactory.createAsyncConnection(checkConnectionConfig))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess { connection ->
                        logger.info("🚀 - Successfully established a new HBase connection")
                        connection.close()
                    }.flatMap {
                        Mono.fromFuture(ConnectionFactory.createAsyncConnection(config))
                    }.cache()

            return HBaseStorageBackend(
                mock = false,
                connectionMono = connectionMono,
                namespace = namespace,
                config = config,
            )
        }

        fun create(
            connectionMono: Mono<AsyncConnection>,
            namespace: String,
            configuration: Configuration,
        ): HBaseStorageBackend =
            HBaseStorageBackend(
                mock = false,
                connectionMono = connectionMono,
                namespace = namespace,
                config = configuration,
            )
    }
}
