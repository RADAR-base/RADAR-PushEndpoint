package org.radarbase.push.integration.garmin.factory

import java.util.function.Supplier
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context

@Suppress("UNCHECKED_CAST")
class GarminAuthMetadataFactory(
    @Context private val requestContext: ContainerRequestContext
) : Supplier<Map<String, String>> {
    override fun get(): Map<String, String> =
        requestContext.getProperty("auth_metadata") as Map<String, String>
}
