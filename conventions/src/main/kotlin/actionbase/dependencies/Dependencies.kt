@file:Suppress("SpellCheckingInspection")

package actionbase.dependencies

object Versions {
    const val SPOTLESS = "7.0.2"
    const val JUNIT = "5.10.2"
    const val JUNIT_JUPITER = "5.10.0"
    const val JUNIT_PLATFORM = "1.10.0"
    const val MOCKK = "1.14.2"
    const val JACKSON = "2.15.3"
    const val IMMUTABLES = "2.9.3"
    const val LZ4 = "1.7.1"
    const val KOTLIN = "1.9.0"
    const val REACTOR = "3.6.2"
    const val REACTIVE_STREAMS = "1.0.4"
    const val FEIGN = "12.4"
    const val OKHTTP = "4.12.0"
    const val SCALA = "2.12.18"
    const val SCALA_BINARY = "2.12"
    const val SLF4J = "2.0.7"
    const val LOGBACK = "1.5.18"
    const val HBASE = "2.5.10"
    const val HGRAPHDB = "3.2.0"
    const val SPARK = "3.4.2"
    const val REACTOR_KOTLIN_EXTENSIONS = "1.2.2"
    const val MYSQL_CONNECTOR = "8.0.32"
    const val SPRING_CLOUD = "2025.0.0"
    const val CAFFEINE = "3.1.8"
    const val JAKARTA_VALIDATION = "3.0.2"
    const val KAFKA_CLIENTS = "3.6.1"
    const val HIKARICP = "5.0.1"
    const val EXPOSED = "0.42.1"
    const val H2 = "2.2.220"
    const val KOTEST = "5.5.5"
    const val BLOCKHOUND = "1.0.8.RELEASE"
    const val RELOAD4J = "1.2.25"
    const val JACKSON_SPARK = "2.12.3"
    const val JUNIT_JUPITER_OLD = "5.9.2"
}

object Dependencies {
    object Testing {
        const val JUNIT_BOM = "org.junit:junit-bom:${Versions.JUNIT}"
        const val JUPITER_API = "org.junit.jupiter:junit-jupiter-api"
        const val JUPITER_ENGINE = "org.junit.jupiter:junit-jupiter-engine"
        const val JUPITER_PARAMS = "org.junit.jupiter:junit-jupiter-params"
        const val JUPITER = "org.junit.jupiter:junit-jupiter:${Versions.JUNIT_JUPITER_OLD}"
        const val PLATFORM_LAUNCHER = "org.junit.platform:junit-platform-launcher"
        const val PLATFORM_ENGINE = "org.junit.platform:junit-platform-engine"
        const val MOCKK = "io.mockk:mockk:${Versions.MOCKK}"
        const val KOTEST_ASSERTIONS_CORE = "io.kotest:kotest-assertions-core:${Versions.KOTEST}"
        const val KOTEST_RUNNER_JUNIT5 = "io.kotest:kotest-runner-junit5:${Versions.KOTEST}"
        const val BLOCKHOUND = "io.projectreactor.tools:blockhound:${Versions.BLOCKHOUND}"
    }

    object Immutables {
        const val VALUE = "org.immutables:value:${Versions.IMMUTABLES}"
        const val VALUE_ANNOTATIONS = "org.immutables:value-annotations:${Versions.IMMUTABLES}"
    }

    object Jackson {
        const val JACKSON_CORE = "com.fasterxml.jackson.core:jackson-core:${Versions.JACKSON}"
        const val JACKSON_DATABIND = "com.fasterxml.jackson.core:jackson-databind:${Versions.JACKSON}"
        const val JACKSON_ANNOTATIONS = "com.fasterxml.jackson.core:jackson-annotations:${Versions.JACKSON}"
        const val JACKSON_YAML = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${Versions.JACKSON}"
        const val JACKSON_KOTLIN = "com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.JACKSON}"
        const val JACKSON_CBOR = "com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:${Versions.JACKSON}"
        const val JACKSON_SMILE = "com.fasterxml.jackson.dataformat:jackson-dataformat-smile:${Versions.JACKSON}"

        // For Apache Spark compatibility
        const val JACKSON_ANNOTATIONS_SPARK = "com.fasterxml.jackson.core:jackson-annotations:${Versions.JACKSON_SPARK}"
        const val JACKSON_DATABIND_SPARK = "com.fasterxml.jackson.core:jackson-databind:${Versions.JACKSON_SPARK}"
    }

    object LZ4 {
        const val LZ4 = "org.lz4:lz4-java:${Versions.LZ4}"
    }

    object Kotlin {
        const val STDLIB = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.KOTLIN}"
        const val REFLECT = "org.jetbrains.kotlin:kotlin-reflect:${Versions.KOTLIN}"
    }

    object Reactor {
        const val CORE = "io.projectreactor:reactor-core:${Versions.REACTOR}"
        const val REACTIVE_STREAMS = "org.reactivestreams:reactive-streams:${Versions.REACTIVE_STREAMS}"
        const val KOTLIN_EXTENSIONS = "io.projectreactor.kotlin:reactor-kotlin-extensions:${Versions.REACTOR_KOTLIN_EXTENSIONS}"
        const val KAFKA = "io.projectreactor.kafka:reactor-kafka"

        const val TEST = "io.projectreactor:reactor-test:${Versions.REACTOR}"
    }

    object Feign {
        const val CORE = "io.github.openfeign:feign-core:${Versions.FEIGN}"
        const val JACKSON = "io.github.openfeign:feign-jackson:${Versions.FEIGN}"
        const val SLF4J = "io.github.openfeign:feign-slf4j:${Versions.FEIGN}"
        const val MOCK = "io.github.openfeign:feign-mock:${Versions.FEIGN}"
        const val OKHTTP = "io.github.openfeign:feign-okhttp:${Versions.FEIGN}"
    }

    object Okhttp3 {
        const val OKHTTP = "com.squareup.okhttp3:okhttp:${Versions.OKHTTP}"
    }

    object HBase {
        const val CLIENT = "org.apache.hbase:hbase-shaded-client:${Versions.HBASE}"
        const val MAPREDUCE = "org.apache.hbase:hbase-shaded-mapreduce:${Versions.HBASE}"
        const val TESTING_UTIL = "org.apache.hbase:hbase-shaded-testing-util:${Versions.HBASE}"
        const val HGRAPHDB = "io.hgraphdb:hgraphdb:${Versions.HGRAPHDB}"
    }

    object Logging {
        const val SLF4J_API = "org.slf4j:slf4j-api:${Versions.SLF4J}"
        const val SLF4J_SIMPLE = "org.slf4j:slf4j-simple:${Versions.SLF4J}"
        const val LOGBACK_CLASSIC = "ch.qos.logback:logback-classic:${Versions.LOGBACK}"
        const val RELOAD4J = "ch.qos.reload4j:reload4j:${Versions.RELOAD4J}"
    }

    object Spark {
        const val CORE = "org.apache.spark:spark-core_${Versions.SCALA_BINARY}:${Versions.SPARK}"
        const val SQL = "org.apache.spark:spark-sql_${Versions.SCALA_BINARY}:${Versions.SPARK}"
        const val STREAMING = "org.apache.spark:spark-streaming_${Versions.SCALA_BINARY}:${Versions.SPARK}"
        const val MLLIB = "org.apache.spark:spark-mllib_${Versions.SCALA_BINARY}:${Versions.SPARK}"
    }

    object Spring {
        const val BOOT_STARTER_WEBFLUX = "org.springframework.boot:spring-boot-starter-webflux"
        const val BOOT_STARTER_VALIDATION = "org.springframework.boot:spring-boot-starter-validation"
        const val BOOT_STARTER_TEST = "org.springframework.boot:spring-boot-starter-test"
        const val DATA_COMMONS = "org.springframework.data:spring-data-commons"
        const val KAFKA = "org.springframework.kafka:spring-kafka"
        const val CLOUD_DEPENDENCIES = "org.springframework.cloud:spring-cloud-dependencies:${Versions.SPRING_CLOUD}"
    }

    object Database {
        const val MYSQL_CONNECTOR = "mysql:mysql-connector-java:${Versions.MYSQL_CONNECTOR}"
        const val H2 = "com.h2database:h2:${Versions.H2}"
    }

    object Cache {
        const val CAFFEINE = "com.github.ben-manes.caffeine:caffeine:${Versions.CAFFEINE}"
    }

    object Validation {
        const val JAKARTA_VALIDATION_API = "jakarta.validation:jakarta.validation-api:${Versions.JAKARTA_VALIDATION}"
    }

    object Kafka {
        const val CLIENTS = "org.apache.kafka:kafka-clients:${Versions.KAFKA_CLIENTS}"
    }

    object DatabasePool {
        const val HIKARICP = "com.zaxxer:HikariCP:${Versions.HIKARICP}"
    }

    object Exposed {
        const val CORE = "org.jetbrains.exposed:exposed-core:${Versions.EXPOSED}"
        const val JAVA_TIME = "org.jetbrains.exposed:exposed-java-time:${Versions.EXPOSED}"
        const val DAO = "org.jetbrains.exposed:exposed-dao:${Versions.EXPOSED}"
        const val JDBC = "org.jetbrains.exposed:exposed-jdbc:${Versions.EXPOSED}"
    }
}
