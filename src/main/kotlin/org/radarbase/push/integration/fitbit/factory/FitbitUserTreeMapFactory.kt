package org.radarbase.push.integration.fitbit.factory

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.push.integration.common.user.User
import java.util.function.Supplier

@Suppress("UNCHECKED_CAST")
class FitbitUserTreeMapFactory(
    @Context private val requestContext: ContainerRequestContext
) : Supplier<Map<User, JsonNode>> {
    override fun get(): Map<User, JsonNode> =
        requestContext.getProperty("user_tree_map") as Map<User, JsonNode>
}

