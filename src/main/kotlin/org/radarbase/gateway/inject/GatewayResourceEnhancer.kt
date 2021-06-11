package org.radarbase.gateway.inject

import jakarta.inject.Singleton
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.message.DeflateEncoder
import org.glassfish.jersey.message.GZipEncoder
import org.glassfish.jersey.server.filter.EncodingFilter
import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.*
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.jersey.service.HealthService
import org.radarbase.producer.rest.SchemaRetriever

class GatewayResourceEnhancer(private val config: Config): JerseyResourceEnhancer {

    override val classes: Array<Class<*>> = arrayOf(
            EncodingFilter::class.java,
            GZipEncoder::class.java,
            DeflateEncoder::class.java,
            ConfigLoader.Filters.logResponse)

    override fun AbstractBinder.enhance() {
        bind(config)
                .to(Config::class.java)

        // Bind factories.
        bindFactory(SchemaRetrieverFactory::class.java)
                .to(SchemaRetriever::class.java)
                .`in`(Singleton::class.java)

        bindFactory(ProducerPoolFactory::class.java)
                .to(ProducerPool::class.java)
                .`in`(Singleton::class.java)

        bindFactory(KafkaAdminServiceFactory::class.java)
                .to(KafkaAdminService::class.java)
                .`in`(Singleton::class.java)

        bind(KafkaHealthMetric::class.java)
                .named("kafka")
                .to(HealthService.Metric::class.java)
                .`in`(Singleton::class.java)
    }
}
