package org.radarbase.push.integration.garmin.factory

import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.push.integration.common.user.User
import java.util.function.Supplier
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context

@Suppress("UNCHECKED_CAST")
class GarminUserTreeMapFactory(
    @Context private val requestContext: ContainerRequestContext
) : Supplier<Map<User, JsonNode>> {
    override fun get(): Map<User, JsonNode> =
        requestContext.getProperty("user_tree_map") as Map<User, JsonNode>
}
