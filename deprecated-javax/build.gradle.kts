
plugins {
    id("com.gradleup.shadow") version "9.6.1"
    java
}

repositories {
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    val lzVersion: String by project
    constraints {
        // kafka-clients pulls in lz4-java 1.10.1 (CVE-2026-59949).
        implementation("at.yawk.lz4:lz4-java:$lzVersion")
    }
    val kafkaVersion: String by project
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion") {
        isTransitive = true
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    val confluentVersion: String by project
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion") {
        isTransitive = false
    }
    implementation("io.confluent:kafka-schema-serializer:$confluentVersion") {
        isTransitive = false
    }
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion") {
        isTransitive = false
    }
    implementation("org.glassfish.jersey.core:jersey-common:3.1.5")
    implementation("io.swagger:swagger-annotations:1.6.2")
    implementation("io.confluent:common-utils:$confluentVersion") {
        isTransitive = false
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.compileClasspath.get())
    relocate("javax.ws.rs", "shadow.javax.ws.rs")
    relocate("org.glassfish", "shadow.org.glassfish")
}
