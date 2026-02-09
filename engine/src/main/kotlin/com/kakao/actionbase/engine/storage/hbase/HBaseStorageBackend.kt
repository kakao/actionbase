package com.kakao.actionbase.engine.storage.hbase

import com.kakao.actionbase.engine.storage.StorageBackend
import com.kakao.actionbase.engine.storage.StorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.security.UserGroupInformation
import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class HBaseStorageBackend private constructor(
    private val connectionMono: Mono<AsyncConnection>,
) : StorageBackend {
    override fun getStorageTable(
        namespace: String,
        name: String,
    ): Mono<StorageTable> =
        connectionMono.map { conn ->
            val table = conn.getTable(TableName.valueOf(namespace, name))
            val hbaseTable = HBaseTable.create(table)
            HBaseStorageTable(hbaseTable)
        }

    override fun close() {
        connectionMono.block()?.close()
    }

    companion object {
        const val DEFAULT_HBASE_NAMESPACE = "default"
        const val LEGACY_DEFAULT_KERBEROS_REALM = "KAKAO.HADOOP"

        private val logger = LoggerFactory.getLogger(HBaseStorageBackend::class.java)

        /**
         * Creates HBaseStorageBackend from properties.
         *
         * # Properties
         *   secure: true or false
         *   version: 2.4 or 2.5
         *   namespace: HBase namespace
         *
         * # for 2.4
         *   hbase.zookeeper.quorum: host1:2181,host2:2181,host3:2181
         * # for 2.5
         *   hbase.client.bootstrap.servers: host1:16000,host2:16000,host3:16000
         *
         * # for secure cluster
         *   kerberos.realm: e.g. EXAMPLE.COM (or env AB_KERBEROS_REALM)
         *     - If missing, defaults to KAKAO.HADOOP for backward compatibility (deprecated)
         *   krb5ConfPath: /path/to/krb5.conf (or env AB_KRB5_CONF_PATH)
         *   keytabPath: e.g. /path/to/hadoop-cdl-write.keytab (or env AB_KEYTAB_PATH)
         *   principal: e.g. hadoop-cdl-write@EXAMPLE.COM (or env AB_PRINCIPAL)
         */
        fun create(properties: Map<String, String>): HBaseStorageBackend {
            logger.info("HBaseStorageBackend is being initialized.")

            val config = HBaseConfiguration.create()

            val isSecure = properties["secure"]?.toBoolean() ?: false
            val version = properties["version"] ?: "2.4"
            requireNotNull(properties["namespace"]) { "HBase namespace is not set" }

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
                val kerberosRealm = resolveKerberosRealm(properties)

                System.setProperty("java.security.krb5.conf", krb5ConfPath)

                config["hadoop.security.authentication"] = "kerberos"
                config["hbase.security.authentication"] = "kerberos"
                config["hbase.master.kerberos.principal"] = "hbase/_HOST@$kerberosRealm"
                config["hbase.regionserver.kerberos.principal"] = "hbase/_HOST@$kerberosRealm"

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
                if (key.startsWith("hbase.") || key.startsWith("hadoop.")) {
                    config[key] = value
                }
            }

            if (isSecure) {
                logger.info("🚀 - Using secure HBase cluster with Kerberos authentication")
                UserGroupInformation.setConfiguration(config)
            }

            val checkConnectionConfig = Configuration(config)
            // For HBase 2.4.x
            checkConnectionConfig.setInt("zookeeper.recovery.retry", 1)
            checkConnectionConfig.setInt("hbase.client.retries.number", 1)

            // For HBase 2.5+
            checkConnectionConfig.setInt("hbase.client.connection.registry.impl.retry", 1)
            checkConnectionConfig.setInt("hbase.client.registry.timeout", 10000)
            checkConnectionConfig.setInt("hbase.client.operation.timeout", 10000)
            checkConnectionConfig.setInt("hbase.rpc.timeout", 10000)

            val connectionMono =
                Mono
                    .fromFuture(ConnectionFactory.createAsyncConnection(checkConnectionConfig))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess { conn ->
                        logger.info("🚀 - Successfully established a new HBase connection")
                        conn.close()
                    }.flatMap {
                        Mono.fromFuture(ConnectionFactory.createAsyncConnection(config))
                    }.cache()

            return HBaseStorageBackend(connectionMono)
        }

        internal fun resolveKerberosRealm(
            properties: Map<String, String>,
            envKerberosRealm: String? = System.getenv("AB_KERBEROS_REALM"),
        ): String {
            val kerberosRealm = (properties["kerberos.realm"] ?: envKerberosRealm)?.trim()

            if (kerberosRealm == null) {
                logger.warn(
                    "`kerberos.realm` is not set; falling back to legacy default realm `{}` for backward compatibility. This fallback is deprecated and will be removed in a future release.",
                    LEGACY_DEFAULT_KERBEROS_REALM,
                )
                // TODO(ab#180): Remove legacy fallback and require explicit kerberos.realm after migration period.
                return LEGACY_DEFAULT_KERBEROS_REALM
            }

            require(kerberosRealm.isNotEmpty()) { "Kerberos realm must not be blank" }
            return kerberosRealm
        }
    }
}
