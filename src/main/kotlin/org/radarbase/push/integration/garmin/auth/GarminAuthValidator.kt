package org.radarbase.push.integration.garmin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.container.ContainerRequestContext
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Base auth validator for Garmin push notifications.
 * Subclasses implement OAuth1 or OAuth2 specific token extraction and authorization checks.
 */
abstract class GarminAuthValidator(
    protected val objectMapper: ObjectMapper,
    protected val userRepository: GarminUserRepository,
) : AuthValidator {

    @Volatile
    private var nextRetry: Instant = Instant.MIN

    override fun verify(token: String, request: ContainerRequestContext): Auth {
        if (token.isBlank()) {
            throw HttpUnauthorizedException("invalid_token", "The token was empty")
        }

        var isAnyUnauthorised = false
        // Enrich the request by adding the User
        // the data format in Garmin's post is { <data-type> : [ {<data-1>}, {<data-2>} ] }
        val tree = request.getProperty("tree") as JsonNode

        val userTreeMap: Map<User, JsonNode> = tree[tree.fieldNames().next()]
            // group by user ID since request can contain data from multiple users
            .groupBy { node ->
                    node[USER_ID_KEY].asText()
                }
                .filter { (userId, userData) ->
                    if (checkIsAuthorised(userId, userData)) {
                        true
                    } else {
                        isAnyUnauthorised = true
                        handleUnauthorised(userId, userData)
                        false
                    }
                }
                .entries
                .associate { (userId, userData) ->
                    userRepository.findByExternalId(userId) to
                        // Map the List<JsonNode> back to <data-type>: [ {<data-1>}, {<data-2>} ]
                        // so it can be processed in the services without much refactoring
                        objectMapper.createObjectNode()
                            .set(tree.fieldNames().next(), objectMapper.valueToTree(userData))
                }

        request.setProperty("user_tree_map", userTreeMap)
        request.setProperty(
            "auth_metadata",
            mapOf("isAnyUnauthorised" to isAnyUnauthorised.toString()),
        )
        request.removeProperty("tree")

        return DisabledAuth("res_gateway")
    }

    override fun getToken(request: ContainerRequestContext): String? {
        if (!request.hasEntity()) return null

        val tree = objectMapper.readTree(request.entityStream)
        request.setProperty("tree", tree)
        return extractToken(tree, request)
    }

    /**
     * Extract the token string from the parsed JSON tree.
     * For OAuth1 this is the userAccessToken from the payload;
     * for OAuth2 this is the userId (since the payload contains no token).
     */
    protected abstract fun extractToken(tree: JsonNode, request: ContainerRequestContext): String

    /**
     * Check whether the given user (identified by userId) with associated data nodes is authorised.
     */
    protected abstract fun checkIsAuthorised(userId: String, userData: List<JsonNode>): Boolean

    /**
     * Called when a user fails authorization. Override to perform cleanup such as deregistration.
     * The default implementation is a no-op.
     */
    protected abstract fun handleUnauthorised(userId: String, userData: List<JsonNode>)

    /**
     * Resolve a [User] by external ID, retrying once after refreshing the user cache
     * if the user is not found and enough time has passed since the last refresh.
     *
     * @return the resolved [User], or `null` if the user cannot be found.
     */
    protected fun resolveUser(userId: String, retry: Boolean = true): User? {
        return try {
            userRepository.findByExternalId(userId)
        } catch (_: NoSuchElementException) {
            if (retry && Instant.now() > nextRetry) {
                userRepository.applyPendingUpdates()
                nextRetry = Instant.now().plusSeconds(REFRESH_TIMEOUT_S)
                resolveUser(userId, retry = false)
            } else {
                logger.warn("The user {} could not be found in the user repository", userId)
                null
            }
        }
    }

    companion object {
        const val USER_ID_KEY = "userId"
        const val USER_ACCESS_TOKEN_KEY = "userAccessToken"
        const val REFRESH_TIMEOUT_S = 5L

        private val logger = LoggerFactory.getLogger(GarminAuthValidator::class.java)
    }
}

