package org.radarbase.push.integration.fitbit.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpNotFoundException
import org.radarbase.push.integration.common.user.User

class FitbitAuthValidator(
    @Context val objectMapper: ObjectMapper,
    @Context val config: Config,
) : AuthValidator {

    override fun verify(token: String, request: ContainerRequestContext): Auth {
        return if (token.isBlank()) {
            throw HttpNotFoundException("not_found", "Signature was not found")
        } else {

            val tree: JsonNode? = if (request.hasEntity()) {
                // We put the json tree in the request because the entity stream will be closed here
                val tree1 = objectMapper.readTree(request.entityStream)
                request.setProperty("tree", tree1)
                tree1
            } else null

            if (!isSignatureValid(token, tree)) {
                throw HttpNotFoundException("invalid_signature", "Valid Signature not found")
            }

            if (!checkIsUserAuthorized(request, tree)) {
                request.setProperty("user_tree_map", null)
            }

            // Disable auth since we don't have proper auth support
            DisabledAuth("res_gateway")
        }
    }

    override fun getToken(request: ContainerRequestContext): String = request.getHeaderString("X-Fitbit-Signature")
        ?: throw HttpNotFoundException("not_found", "Signature was not found")


    fun checkIsUserAuthorized(request: ContainerRequestContext, tree: JsonNode?): Boolean {

        if (tree == null) {
            return false
        }

        val userTreeMap: Map<User, JsonNode> = TODO(
            "check the all the users contained in the request exist in the user repository" +
                " and every user is authorized, and map each valid user's data, check garmin for reference"
        )

        TODO("If the user does not exist in the repo, then return unauthorized http code and immediately unsubscribe the user from fitbit")

        TODO("put all the users in request context")
        request.setProperty("user_tree_map", userTreeMap)
    }


    fun isSignatureValid(signature: String?, contents: JsonNode?): Boolean {
        val signingKey = "${config.pushIntegration.fitbit.clientSecret}&"

        if (signature == null) {
            return false
        }
        /*
        X-Fitbit-Signature
            To confirm that a notification originated from Fitbit you may verify the X-Fitbit-Signature HTTP header value. Compute the expected signature using the following method:

            Look up the client secret listed for your application on dev.fitbit.com
            Append the & character to the client secret to form the signing key, e.g. 123ab4567c890d123e4567f8abcdef9a&
            Using a cryptographic library, hash the JSON body of the notification with the HMAC-SHA1 algorithm and the above signing key. The body begins with a [ character and ends with a ] character, inclusive.
            BASE64 encode the result of the hash function.
            Finally, verify the BASE64 encoded value matches the value of the X-Fitbit-Signature header.
            NOTE: This method is similar to the Authorization Header oauth_signature parameter described in RFC5849 but does not utilize parameter encoding.
            If signature verification fails, respond with a 404 to avoid revealing your application to a potential attacker. We recommend logging the remote IP of the host sending the incorrect signature, the incoming signature, and incoming message content. We ask that you send us a copy of this information so we can investigate.

            Signature verification is optional, but recommended.
         */
        TODO("fix signature verification, look at https://dev.fitbit.com/build/reference/web-api/developer-guide/best-practices/#Subscriber-Security")

        return true
    }
}
