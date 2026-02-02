plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.diffplug.spotless") version "7.0.3"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("StyleConventionsPlugin") {
            id = "actionbase.style-conventions"
            implementationClass = "actionbase.StyleConventionsPlugin"
        }
        create("BaseConventionsPlugin") {
            id = "actionbase.base-conventions"
            implementationClass = "actionbase.BaseConventionsPlugin"
        }
        create("Java8ConventionsPlugin") {
            id = "actionbase.java8-conventions"
            implementationClass = "actionbase.Java8ConventionsPlugin"
        }
        create("KotlinConventionsPlugin") {
            id = "actionbase.kotlin-conventions"
            implementationClass = "actionbase.KotlinConventionsPlugin"
        }
        create("PlatformConventionsPlugin") {
            id = "actionbase.platform-conventions"
            implementationClass = "actionbase.PlatformConventionsPlugin"
        }
        create("ReactorConventionsPlugin") {
            id = "actionbase.reactor-conventions"
            implementationClass = "actionbase.ReactorConventionsPlugin"
        }
        create("SparkConventionsPlugin") {
            id = "actionbase.spark-conventions"
            implementationClass = "actionbase.SparkConventionsPlugin"
        }
        create("SpringConventionsPlugin") {
            id = "actionbase.spring-conventions"
            implementationClass = "actionbase.SpringConventionsPlugin"
        }
        create("JibConventionsPlugin") {
            id = "actionbase.jib-conventions"
            implementationClass = "actionbase.JibConventionsPlugin"
        }
    }
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.0")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.3")
    implementation("com.github.johnrengelman:shadow:8.1.1")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.4.0")
}

spotless {
    kotlin {
        ktlint()
        target("**/*.kt", "**/*.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
