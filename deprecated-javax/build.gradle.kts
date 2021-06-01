
plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    java
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
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
    implementation("org.glassfish.jersey.core:jersey-common:2.31")
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
