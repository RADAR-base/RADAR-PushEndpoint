import java.time.Duration
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("idea")
    id("application")
    kotlin("jvm")
    id("org.unbroken-dome.test-sets") version "3.0.1"
    id("com.avast.gradle.docker-compose") version "0.13.4"
}

group = "org.radarbase"
version = "0.5.4-SNAPSHOT"
description = "RADAR Gateway to handle secured data flow to backend."

dependencyLocking {
    lockAllConfigurations()
}

repositories {
    jcenter()
    // Non-jcenter radar releases
    maven(url = "https://dl.bintray.com/radar-cns/org.radarcns")
    maven(url = "https://dl.bintray.com/radar-base/org.radarbase")
    // For working with dev-branches
    maven(url = "https://repo.thehyve.nl/content/repositories/snapshots")
    maven(url = "https://oss.jfrog.org/artifactory/libs-snapshot/")
    maven(url = "https://packages.confluent.io/maven/")
}

val integrationTest = testSets.create("integrationTest")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val radarCommonsVersion: String by project
    implementation("org.radarbase:radar-commons:$radarCommonsVersion")
    implementation("org.radarbase:radar-jersey:${project.property("radarJerseyVersion")}")
    implementation("org.radarbase:lzfse-decode:${project.property("lzfseVersion")}")

    implementation("org.apache.kafka:kafka-clients:${project.property("kafkaVersion")}")
    implementation("io.confluent:kafka-avro-serializer:${project.property("confluentVersion")}")

    implementation("org.slf4j:slf4j-api:${project.property("slf4jVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${project.property("jacksonVersion")}")


    val grizzlyVersion: String by project
    runtimeOnly("org.glassfish.grizzly:grizzly-framework-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-server-monitoring:$grizzlyVersion")
    runtimeOnly("ch.qos.logback:logback-classic:${project.property("logbackVersion")}")

    val junitVersion: String by project
    val okhttp3Version: String by project
    val radarSchemasVersion: String by project
    implementation("org.radarcns:radar-schemas-commons:$radarSchemasVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:[2.2,3.0)")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttp3Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.radarcns:radar-schemas-commons:$radarSchemasVersion")
    integrationTest.implementationConfigurationName("com.squareup.okhttp3:okhttp:$okhttp3Version")
    integrationTest.implementationConfigurationName("org.radarcns:radar-schemas-commons:$radarSchemasVersion")
    integrationTest.implementationConfigurationName("org.radarbase:radar-commons-testing:$radarCommonsVersion")
}

val kotlinApiVersion: String by project

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    useJUnitPlatform()
}

tasks.withType<Tar> {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

application {
    mainClassName = "org.radarbase.gateway.MainKt"

    applicationDefaultJvmArgs = listOf(
            "-Dcom.sun.management.jmxremote",
            "-Dcom.sun.management.jmxremote.local.only=false",
            "-Dcom.sun.management.jmxremote.port=9010",
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=false"
    )
}

dockerCompose {
    useComposeFiles = listOf("src/integrationTest/docker/docker-compose.yml")
    buildAdditionalArgs = emptyList<String>()
    val dockerComposeStopContainers: String? by project
    stopContainers = dockerComposeStopContainers?.toBooleanLenient() ?: true
    waitForTcpPortsTimeout = Duration.ofMinutes(3)
    environment["SERVICES_HOST"] = "localhost"
    isRequiredBy(tasks["integrationTest"])
}

idea {
    module {
        isDownloadSources = true
    }
}


tasks.register("downloadDockerDependencies") {
    doFirst {
        configurations["compileClasspath"].files
        configurations["runtimeClasspath"].files
        println("Downloaded all dependencies")
    }
    outputs.upToDateWhen { false }
}

tasks.register("downloadDependencies") {
    doFirst {
        configurations.asMap
                .filterValues { it.isCanBeResolved }
                .forEach { (name, config) ->
                    try {
                        config.files
                    } catch (ex: Exception) {
                        project.logger.warn("Cannot find dependency for configuration {}", name, ex)
                    }
                }
        println("Downloaded all dependencies")
    }
    outputs.upToDateWhen { false }
}

tasks.wrapper {
    gradleVersion = "6.6.1"
}
