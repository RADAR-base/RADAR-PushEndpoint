package org.radarbase.push.integration

import jakarta.inject.Singleton
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.FITBIT_QUALIFIER
import org.radarbase.push.integration.fitbit.auth.FitbitAuthValidator
import org.radarbase.push.integration.fitbit.service.FitbitApiService
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository

class FitbitPushIntegrationResourceEnhancer(private val config: Config) : JerseyResourceEnhancer {
    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integration.fitbit.resource",
            "org.radarbase.push.integration.common.filter"
        )
    }

    override fun AbstractBinder.enhance() {

        bind(config.pushIntegration.fitbit.userRepository)
            .to(FitbitUserRepository::class.java)
            .named(FITBIT_QUALIFIER)
            .`in`(Singleton::class.java)

        bind(FitbitAuthValidator::class.java)
            .to(AuthValidator::class.java)
            .named(FITBIT_QUALIFIER)
            .`in`(Singleton::class.java)

        bind(FitbitApiService::class.java)
            .to(FitbitApiService::class.java)
            .`in`(Singleton::class.java)
    }
}