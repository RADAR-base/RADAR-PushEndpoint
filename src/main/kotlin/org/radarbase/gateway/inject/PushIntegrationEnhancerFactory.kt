package org.radarbase.gateway.inject

import okhttp3.internal.toImmutableList
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.auth.MPConfig
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.jersey.config.EnhancerFactory
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integration.GarminPushIntegrationResourceEnhancer
import org.radarbase.push.integration.common.inject.PushIntegrationResourceEnhancer

class PushIntegrationEnhancerFactory(private val config: Config) : EnhancerFactory {

    val authConfig = AuthConfig(
        managementPortal = MPConfig(
            url = config.auth.managementPortalUrl
        ),
        jwtResourceName = config.auth.resourceName,
        jwtIssuer = config.auth.issuer
    )

    override fun createEnhancers(): List<JerseyResourceEnhancer> {

        val enhancersList = mutableListOf(
            GatewayResourceEnhancer(config),
            ConfigLoader.Enhancers.radar(authConfig),
            ConfigLoader.Enhancers.health,
            ConfigLoader.Enhancers.httpException,
            ConfigLoader.Enhancers.generalException,
            PushIntegrationResourceEnhancer()
        )

        if (config.pushIntegration.garmin.enabled) {
            enhancersList.add(GarminPushIntegrationResourceEnhancer(config))
        }
        // Add more enhancers as services are added

        return enhancersList.toImmutableList()
    }
}
