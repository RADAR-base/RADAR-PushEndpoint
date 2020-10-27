package org.radarbase.gateway.inject

import okhttp3.internal.toImmutableList
import org.radarbase.gateway.Config
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.jersey.config.EnhancerFactory
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integrations.GarminPushIntegrationResourceEnhancer

class PushIntegrationEnhancerFactory(private val config: Config) : EnhancerFactory {

    override fun createEnhancers(): List<JerseyResourceEnhancer> {

        val enhancersList = mutableListOf(
            GatewayResourceEnhancer(config),
            ConfigLoader.Enhancers.health,
            ConfigLoader.Enhancers.httpException,
            ConfigLoader.Enhancers.generalException
        )
        enhancersList.addAll(
            when {
                config.pushIntegrationConfig.enabledPushIntegrations.contains("garmin") ->
                    listOf(
                        // TODO replace with GarminUserAuthorization
                        ConfigLoader.Enhancers.disabledAuthorization,
                        GarminPushIntegrationResourceEnhancer(config),
                    )
                // Add more configs as the integrations are added
                else -> throw IllegalStateException(
                    "The configured push integration is not " +
                            "available."
                )
            }
        )
        return enhancersList.toImmutableList()
    }
}
