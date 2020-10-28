package org.radarbase.push.integration.garmin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.UserRepository
import org.slf4j.LoggerFactory
import javax.inject.Named
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context


class GarminAuthValidator(
    @Context private val objectMapper: ObjectMapper,
    @Named(GARMIN_QUALIFIER) private val userRepository: UserRepository
) :
    AuthValidator {
    override fun verify(token: String, request: ContainerRequestContext): Auth? {
        return if (token.isBlank()) {
            throw HttpUnauthorizedException("invalid_token", "The token was not empty")
        } else {
            // Enrich the request by adding the User
            // the data format in Garmin's post is { <data-type> : [ {<data-1>}, {<data-2>} ] }
            val tree = request.getProperty("tree") as JsonNode
            val userId = tree[tree.fieldNames().next()][0][USER_ID_KEY]
                ?: throw HttpUnauthorizedException("invalid_user", "No user id provided")
            val user = try {
                userRepository.findByExternalId(userId.asText())
            } catch (exc: NoSuchElementException) {
                throw HttpUnauthorizedException(
                    "no_user", "The user could not be found in the " +
                            "user repository"
                )
            }

            // Compare access tokens for authenticity of the request
            if (userRepository.getAccessToken(user) == token) {
                request.setProperty("user", user)
            } else {
                throw HttpUnauthorizedException(
                    "invalid_user", "The token for user ${user.id} does not" +
                            " match with the records on the system"
                )
            }

            // Disable auth since we don't have proper auth support
            DisabledAuth("res_gateway")
        }
    }

    override fun getToken(request: ContainerRequestContext): String? {
        return if (request.hasEntity()) {
            // We put the json tree in the request because the entity stream will be closed here
            val tree = objectMapper.readTree(request.entityStream)
            request.setProperty("tree", tree)
            val userAccessToken = tree[tree.fieldNames().next()][0][USER_ACCESS_TOKEN_KEY]
                ?: throw HttpUnauthorizedException("invalid_token", "No user access token provided")
            userAccessToken.asText().also {
                request.setProperty(USER_ACCESS_TOKEN_KEY, it)
            }
        } else {
            null
        }
    }

    companion object {
        const val USER_ID_KEY = "userId"
        const val USER_ACCESS_TOKEN_KEY = "userAccessToken"

        private val logger = LoggerFactory.getLogger(GarminAuthValidator::class.java)
    }
}
