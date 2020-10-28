package org.radarbase.push.integrations

import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integrations.garmin.auth.GarminAuthValidator
import org.radarbase.push.integrations.garmin.service.GarminHealthApiService
import org.radarbase.push.integrations.common.user.UserRepository
import javax.inject.Singleton

class GarminPushIntegrationResourceEnhancer(private val config: Config) :
    JerseyResourceEnhancer {

    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integrations.garmin.resource",
            "org.radarbase.push.integrations.common.filter"
        )
    }

    override fun AbstractBinder.enhance() {

        bind(config.pushIntegrationConfig.userRepository)
            .to(UserRepository::class.java)
            .`in`(Singleton::class.java)

        bind(GarminHealthApiService::class.java)
            .to(GarminHealthApiService::class.java)
            .`in`(Singleton::class.java)

        bind(GarminAuthValidator::class.java)
            .to(AuthValidator::class.java)
            .`in`(Singleton::class.java)
    }
}
