package org.radarbase.gateway

import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.radarbase.gateway.inject.EnhancerFactory
import org.radarbase.jersey.config.RadarResourceConfigFactory
import java.util.concurrent.TimeUnit

class GrizzlyServer(private val config: Config) {
    private var httpServer: HttpServer? = null

    fun start() {
        val gatewayResources = config.resourceConfig.getConstructor().newInstance()
        val resourceEnhancers = (gatewayResources as EnhancerFactory).createEnhancers(config)
        val resourceConfig = RadarResourceConfigFactory().resources(resourceEnhancers)

        httpServer = GrizzlyHttpServerFactory.createHttpServer(config.baseUri, resourceConfig, false)
                .apply {
                    serverConfiguration.isJmxEnabled = config.isJmxEnabled
                }.also { it.start() }
    }

    fun shutdown() {
        httpServer?.shutdown()?.get(5, TimeUnit.SECONDS)
    }
}
