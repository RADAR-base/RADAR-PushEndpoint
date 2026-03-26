package org.radarbase.push.integration.garmin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Named
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.slf4j.LoggerFactory

/**
 * Auth validator for Garmin OAuth1 push notifications.
 * The push payload contains a `userAccessToken` field which is compared
 * against the token stored in the user repository.
 */
class GarminOAuth1AuthValidator(
    @Context objectMapper: ObjectMapper,
    @Named(GARMIN_QUALIFIER) userRepository: GarminUserRepository,
) : GarminAuthValidator(objectMapper, userRepository) {

    override fun extractToken(tree: JsonNode, request: ContainerRequestContext): String {
        val userAccessToken = tree[tree.fieldNames().next()][0][USER_ACCESS_TOKEN_KEY]
            ?: throw HttpUnauthorizedException("invalid_token", "No user access token provided")
        return userAccessToken.asText().also {
            request.setProperty(USER_ACCESS_TOKEN_KEY, it)
        }
    }

    override fun checkIsAuthorised(userId: String, userData: List<JsonNode>): Boolean {
        val user = resolveUser(userId) ?: return false
        if (!user.isAuthorized) {
            logger.warn("The user {} does not seem to be authorized", userId)
            return false
        }
        val accessToken = userData[0][USER_ACCESS_TOKEN_KEY].asText()
        if (userRepository.getOAuth1AccessToken(user) != accessToken) {
            logger.warn("The token for user {} does not match with the auth records", userId)
            return false
        }
        return true
    }

    override fun handleUnauthorised(userId: String, userData: List<JsonNode>) {
        val accessToken = userData[0][USER_ACCESS_TOKEN_KEY].asText()
        userRepository.deregisterUser(userId, accessToken)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminOAuth1AuthValidator::class.java)
    }
}

