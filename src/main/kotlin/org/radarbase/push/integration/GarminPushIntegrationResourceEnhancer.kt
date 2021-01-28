package org.radarbase.push.integration

import com.fasterxml.jackson.databind.JsonNode
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.auth.GarminAuthValidator
import org.radarbase.push.integration.garmin.factory.GarminJsonNodeFactory
import org.radarbase.push.integration.garmin.factory.GarminUserFactory
import org.radarbase.push.integration.garmin.service.BackfillService
import org.radarbase.push.integration.garmin.service.GarminHealthApiService
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import javax.inject.Singleton

class GarminPushIntegrationResourceEnhancer(private val config: Config) :
    JerseyResourceEnhancer {

    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integration.garmin.resource",
            "org.radarbase.push.integration.common.filter"
        )
    }

    override val classes: Array<Class<*>>
        get() = if (config.pushIntegration.garmin.backfill.enabled) {
            arrayOf(BackfillService::class.java)
        } else {
            emptyArray()
        }

    override fun AbstractBinder.enhance() {

        bind(config.pushIntegration.garmin.userRepository)
            .to(GarminUserRepository::class.java)
            .named(GARMIN_QUALIFIER)
            .`in`(Singleton::class.java)

        bind(GarminHealthApiService::class.java)
            .to(GarminHealthApiService::class.java)
            .`in`(Singleton::class.java)

        bind(GarminAuthValidator::class.java)
            .to(AuthValidator::class.java)
            .named(GARMIN_QUALIFIER)
            .`in`(Singleton::class.java)

        bindFactory(GarminJsonNodeFactory::class.java)
            .to(JsonNode::class.java)
            .proxy(true)
            .named(GARMIN_QUALIFIER)
            .`in`(RequestScoped::class.java)

        bindFactory(GarminUserFactory::class.java)
            .to(User::class.java)
            .proxy(true)
            .named(GARMIN_QUALIFIER)
            .`in`(RequestScoped::class.java)
    }
}
