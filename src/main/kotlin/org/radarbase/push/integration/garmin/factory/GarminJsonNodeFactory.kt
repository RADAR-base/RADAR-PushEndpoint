package org.radarbase.push.integration.garmin.factory

import com.fasterxml.jackson.databind.JsonNode
import java.util.function.Supplier
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class GarminJsonNodeFactory(
    @Context private val requestContext: ContainerRequestContext
) : Supplier<JsonNode> {
    override fun get(): JsonNode = requestContext.getProperty("tree") as JsonNode
}
