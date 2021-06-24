package org.radarbase.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = try {
        ConfigLoader.loadConfig<Config>(
            listOf(
                "gateway.yml",
                "/etc/radar-gateway/gateway.yml"
            ),
            args,
            ObjectMapper(YAMLFactory())
                .registerModule(kotlinModule())
                .registerModule(JavaTimeModule())
        )
            .withDefaults()
    } catch (ex: IllegalArgumentException) {
        logger.error("No configuration file was found.")
        logger.error("Usage: radar-gateway <config-file>")
        exitProcess(1)
    }

    try {
        config.validate()
    } catch (ex: IllegalStateException) {
        logger.error("Configuration incomplete: {}", ex.message)
        exitProcess(1)
    }

    val resources = ConfigLoader.loadResources(config.resourceConfig, config)
    val server = GrizzlyServer(config.server.baseUri, resources, config.server.isJmxEnabled)
    server.listen()
}

private val logger = LoggerFactory.getLogger("org.radarbase.gateway.MainKt")
