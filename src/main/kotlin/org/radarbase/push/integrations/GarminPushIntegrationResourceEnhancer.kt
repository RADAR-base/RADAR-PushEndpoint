package org.radarbase.push.integrations

import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integrations.garmin.service.GarminHealthApiService
import org.radarbase.push.integrations.garmin.user.ServiceUserRepository
import org.radarbase.push.integrations.garmin.user.UserRepository
import javax.inject.Singleton
import javax.ws.rs.core.Context

class GarminPushIntegrationResourceEnhancer(@Context config: Config) : JerseyResourceEnhancer {

    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integrations.garmin.resource",
            "org.radarbase.push.integrations.common.filter"
        )
    }

    override fun AbstractBinder.enhance() {

        bindAsContract(GarminHealthApiService::class.java)
            .`in`(Singleton::class.java)

        bind(UserRepository::class.java)
            .to(ServiceUserRepository::class.java)
    }
}
