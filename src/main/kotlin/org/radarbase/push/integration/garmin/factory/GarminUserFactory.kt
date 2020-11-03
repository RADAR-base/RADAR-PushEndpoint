package org.radarbase.push.integration.garmin.factory

import org.radarbase.push.integration.common.user.User
import java.util.function.Supplier
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class GarminUserFactory(
    @Context private val requestContext: ContainerRequestContext
) : Supplier<User> {
    override fun get(): User = requestContext.getProperty("user") as User
}
