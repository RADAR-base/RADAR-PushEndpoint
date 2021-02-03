package org.radarbase.push.integration.garmin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.slf4j.LoggerFactory
import javax.inject.Named
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context


class GarminAuthValidator(
    @Context private val objectMapper: ObjectMapper,
    @Named(GARMIN_QUALIFIER) private val userRepository: GarminUserRepository
) :
    AuthValidator {

    override fun verify(token: String, request: ContainerRequestContext): Auth {
        return if (token.isBlank()) {
            throw HttpUnauthorizedException("invalid_token", "The token was empty")
        } else {
            var isAnyUnauthorised = false
            // Enrich the request by adding the User
            // the data format in Garmin's post is { <data-type> : [ {<data-1>}, {<data-2>} ] }
            val tree = request.getProperty("tree") as JsonNode

            val userTreeMap: Map<User, JsonNode> =
                // group by user ID since request can contain data from multiple users
                tree[tree.fieldNames().next()].groupBy { node ->
                    node[USER_ID_KEY].asText()
                }.filter { (k, v) ->
                    val user = try {
                        userRepository.findByExternalId(k)
                    } catch (exc: NoSuchElementException) {
                        isAnyUnauthorised = true
                        logger.warn(
                            "no_user: {}", "The user $k could not be found in the " +
                                "user repository."
                        )
                        return@filter false
                    }
                    if (!user.isAuthorized) {
                        userRepository.reportDeregistration(user)
                        isAnyUnauthorised = true
                        logger.warn(
                            "invalid_user: {}", "The user $k does not seem to be authorized."
                        )
                        return@filter false
                    }
                    if (userRepository.getAccessToken(user) != v[0][USER_ACCESS_TOKEN_KEY].asText()
                    ) {
                        isAnyUnauthorised = true
                        userRepository.reportDeregistration(user)
                        logger.warn(
                            "invalid_user: {}", "The token for user $k does not" +
                                " match with the records on the system."
                        )
                        return@filter false
                    }
                    true
                }.mapKeys { (k, _) ->
                    userRepository.findByExternalId(k)
                }.mapValues { (_, v) ->
                    // Map the List<JsonNode> back to <data-type>: [ {<data-1>}, {<data-2>} ]
                    // so it can be processed in the services without much refactoring
                    objectMapper.createObjectNode()
                        .set(tree.fieldNames().next(), objectMapper.valueToTree(v))
                }

            request.setProperty("user_tree_map", userTreeMap)
            request.setProperty(
                "auth_metadata",
                mapOf("isAnyUnauthorised" to isAnyUnauthorised.toString())
            )
            request.removeProperty("tree")

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
