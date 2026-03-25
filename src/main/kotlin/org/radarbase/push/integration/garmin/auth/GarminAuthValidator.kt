package org.radarbase.push.integration.garmin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Named
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.slf4j.LoggerFactory
import java.time.Instant


class GarminAuthValidator(
    @Context private val objectMapper: ObjectMapper,
    @Context private val config: Config,
    @Named(GARMIN_QUALIFIER) private val userRepository: GarminUserRepository
) : AuthValidator {
    private var nextRetry: Instant = Instant.MIN
    private val isOauth2Flow = config.pushIntegration.garmin.oauthVersion.equals("oauth2", ignoreCase = true)

    override fun verify(token: String, request: ContainerRequestContext): Auth {
        return if (token.isBlank()) {
            throw HttpUnauthorizedException("invalid_token", "The token was empty")
        } else {
            var isAnyUnauthorised = false
            val tree = request.getProperty("tree") as JsonNode

            val userTreeMap: Map<User, JsonNode> =
                tree[tree.fieldNames().next()]
                    .groupBy { node ->
                        node[USER_ID_KEY].asText()
                    }
                    .filter { (userId, userData) ->
                        val isAuthorized = if (isOauth2Flow) {
                            checkIsAuthorisedOAuth2(userId)
                        } else {
                            val accessToken = userData[0][USER_ACCESS_TOKEN_KEY].asText()
                            checkIsAuthorisedOAuth1(userId, accessToken)
                        }
                        if (isAuthorized) {
                            true
                        } else {
                            isAnyUnauthorised = true
                            if (!isOauth2Flow) {
                                val accessToken = userData[0][USER_ACCESS_TOKEN_KEY].asText()
                                userRepository.deregisterUser(userId, accessToken)
                            }
                            false
                        }
                    }
                    .entries
                    .associate { (userId, userData) ->
                        userRepository.findByExternalId(userId) to
                            objectMapper.createObjectNode()
                                .set(tree.fieldNames().next(), objectMapper.valueToTree(userData))
                    }

            request.setProperty("user_tree_map", userTreeMap)
            request.setProperty(
                "auth_metadata",
                mapOf("isAnyUnauthorised" to isAnyUnauthorised.toString())
            )
            request.removeProperty("tree")

            DisabledAuth("res_gateway")
        }
    }

    override fun getToken(request: ContainerRequestContext): String? {
        return if (request.hasEntity()) {
            val tree = objectMapper.readTree(request.entityStream)
            request.setProperty("tree", tree)

            if (isOauth2Flow) {
                val userId = tree[tree.fieldNames().next()][0][USER_ID_KEY]?.asText()
                    ?: throw HttpUnauthorizedException("invalid_token", "No user ID provided")
                // OAuth2 push notifications do not include userAccessToken, so use userId
                // as the token identifier for the verify() flow.
                userId
            } else {
                val userAccessToken = tree[tree.fieldNames().next()][0][USER_ACCESS_TOKEN_KEY]
                    ?: throw HttpUnauthorizedException("invalid_token", "No user access token provided")
                userAccessToken.asText().also {
                    request.setProperty(USER_ACCESS_TOKEN_KEY, it)
                }
            }
        } else {
            null
        }
    }

    private fun checkIsAuthorisedOAuth1(userId: String, accessToken: String, retry: Boolean = true): Boolean {
        val user = try {
            userRepository.findByExternalId(userId)
        } catch (_: NoSuchElementException) {
            return if (retry && Instant.now() > nextRetry) {
                userRepository.applyPendingUpdates()
                nextRetry = Instant.now().plusSeconds(REFRESH_TIMEOUT_S)
                checkIsAuthorisedOAuth1(userId, accessToken, retry = false)
            } else {
                logger.warn("The user {} could not be found in the user repository", userId)
                false
            }
        }
        if (!user.isAuthorized) {
            logger.warn("The user {} does not seem to be authorized", userId)
            return false
        }
        if (userRepository.getOAuth1AccessToken(user) != accessToken) {
            logger.warn("The token for user {} does not match with the auth records", userId)
            return false
        }
        return true
    }

    private fun checkIsAuthorisedOAuth2(userId: String, retry: Boolean = true): Boolean {
        val user = try {
            userRepository.findByExternalId(userId)
        } catch (_: NoSuchElementException) {
            return if (retry && Instant.now() > nextRetry) {
                userRepository.applyPendingUpdates()
                nextRetry = Instant.now().plusSeconds(REFRESH_TIMEOUT_S)
                checkIsAuthorisedOAuth2(userId, retry = false)
            } else {
                logger.warn(" The user {} could not be found in the user repository.", userId)
                false
            }
        }
        if (!user.isAuthorized) {
            logger.warn("The user {} does not seem to be authorized.", userId)
            return false
        }
        return true
    }

    companion object {
        const val USER_ID_KEY = "userId"
        const val USER_ACCESS_TOKEN_KEY = "userAccessToken"
        const val REFRESH_TIMEOUT_S = 5L

        private val logger = LoggerFactory.getLogger(GarminAuthValidator::class.java)
    }
}
