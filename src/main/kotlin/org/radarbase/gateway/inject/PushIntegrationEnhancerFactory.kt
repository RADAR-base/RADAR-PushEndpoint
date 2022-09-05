package org.radarbase.gateway.inject

import okhttp3.internal.toImmutableList
import org.radarbase.gateway.Config
import org.radarbase.jersey.enhancer.Enhancers
import org.radarbase.jersey.enhancer.EnhancerFactory
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.push.integration.FitbitPushIntegrationResourceEnhancer
import org.radarbase.push.integration.GarminPushIntegrationResourceEnhancer
import org.radarbase.push.integration.common.inject.PushIntegrationResourceEnhancer

class PushIntegrationEnhancerFactory(private val config: Config) : EnhancerFactory {

    override fun createEnhancers(): List<JerseyResourceEnhancer> {

        val enhancersList = mutableListOf(
            GatewayResourceEnhancer(config),
            Enhancers.health,
            Enhancers.exception,
            RadarResourceEnhancer(),
            PushIntegrationResourceEnhancer()
        )

        if (config.pushIntegration.garmin.enabled) {
            enhancersList.add(GarminPushIntegrationResourceEnhancer(config))
        }
        if (config.pushIntegration.fitbit.enabled) {
            enhancersList.add(FitbitPushIntegrationResourceEnhancer(config))
        }
        // Add more enhancers as services are added

        return enhancersList.toImmutableList()
    }
}
