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
 * Auth validator for Garmin OAuth2 push notifications.
 * The push payload does NOT contain an access token; authorization is checked
 * by verifying the user exists in the repository and is marked as authorized.
 */
class GarminOAuth2AuthValidator(
    @Context objectMapper: ObjectMapper,
    @Named(GARMIN_QUALIFIER) userRepository: GarminUserRepository,
) : GarminAuthValidator(objectMapper, userRepository) {

    override fun extractToken(tree: JsonNode, request: ContainerRequestContext): String {
        return tree[tree.fieldNames().next()][0][USER_ID_KEY]?.asText()
            ?: throw HttpUnauthorizedException("invalid_token", "No user ID provided")
    }


    override fun checkIsAuthorised(userId: String, userData: List<JsonNode>): Boolean {
        val user = resolveUser(userId) ?: return false
        if (!user.isAuthorized) {
            logger.warn("The user {} does not seem to be authorized", userId)
            return false
        }
        return true
    }

    override fun handleUnauthorised(userId: String, userData: List<JsonNode>) {
        val user = try {
            userRepository.findByExternalId(userId)
        } catch (_: NoSuchElementException) {
            return // user not found, nothing to deregister
        }
        val accessToken = try {
            userRepository.getOAuth2AccessToken(user)
        } catch (e: Exception) {
            logger.warn("Could not fetch OAuth2 token for deregistration of user {}", userId, e)
            return
        }
        userRepository.deregisterUser(userId, accessToken)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminOAuth2AuthValidator::class.java)
    }
}

