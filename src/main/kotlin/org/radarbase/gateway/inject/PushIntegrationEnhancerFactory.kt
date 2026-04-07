package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.enhancer.Enhancers
import org.radarbase.jersey.enhancer.EnhancerFactory
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.push.integration.GarminPushIntegrationResourceEnhancer
import org.radarbase.push.integration.common.inject.PushIntegrationResourceEnhancer

class PushIntegrationEnhancerFactory(private val config: Config) : EnhancerFactory {

    override fun createEnhancers(): List<JerseyResourceEnhancer> = buildList {
        add(GatewayResourceEnhancer(config))
        add(Enhancers.health)
        add(Enhancers.exception)
        add(RadarResourceEnhancer())
        add(PushIntegrationResourceEnhancer())

        if (config.pushIntegration.garmin.enabled) {
            add(GarminPushIntegrationResourceEnhancer(config))
        }
    }
}
