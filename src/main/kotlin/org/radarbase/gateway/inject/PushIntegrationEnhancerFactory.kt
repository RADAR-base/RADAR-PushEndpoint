package org.radarbase.gateway.inject

import okhttp3.internal.toImmutableList
import org.radarbase.gateway.Config
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.jersey.config.EnhancerFactory
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integration.GarminPushIntegrationResourceEnhancer
import org.radarbase.push.integration.common.inject.PushIntegrationResourceEnhancer

class PushIntegrationEnhancerFactory(private val config: Config) : EnhancerFactory {

    override fun createEnhancers(): List<JerseyResourceEnhancer> {

        val enhancersList = mutableListOf(
            GatewayResourceEnhancer(config),
            ConfigLoader.Enhancers.health,
            ConfigLoader.Enhancers.httpException,
            ConfigLoader.Enhancers.generalException,
            RadarResourceEnhancer(),
            PushIntegrationResourceEnhancer()
        )

        if (config.pushIntegration.garmin.enabled) {
            enhancersList.add(GarminPushIntegrationResourceEnhancer(config))
        }
        // Add more enhancers as services are added

        return enhancersList.toImmutableList()
    }
}
