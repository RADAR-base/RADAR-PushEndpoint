package org.radarbase.push.integration.fitbit.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Named
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpNotFoundException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.FITBIT_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class FitbitAuthValidator(
    @Context val objectMapper: ObjectMapper,
    @Context val config: Config,
    @Named(FITBIT_QUALIFIER) private val userRepository: FitbitUserRepository
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

    override fun getToken(request: ContainerRequestContext): String =
        request.getHeaderString("X-Fitbit-Signature")
            ?: throw HttpNotFoundException("not_found", "Signature was not found")


    fun checkIsUserAuthorized(request: ContainerRequestContext, tree: JsonNode?): Boolean {

        if (tree == null) {
            return false
        }

        val userTreeMap: Map<User, JsonNode> =
            tree[tree.fieldNames().next()]
                .groupBy { node ->
                    node[USER_ID_KEY].asText()
                }
                .filter { (userId, userData) ->
                    try {
                        userRepository.findByExternalId(userId)
                        true
                    } catch (ex: NoSuchElementException) {
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
        return true
    }


    fun isSignatureValid(signature: String?, contents: JsonNode?): Boolean {
        val signingKey = "${config.pushIntegration.fitbit.clientSecret}&"
        if (signature == null) {
            return false
        }
        if (contents == null) {
            return false
        }
        val genHMAC = genHMAC(contents.asText(), signingKey)
        return genHMAC.equals(signature)
    }

    fun genHMAC(data: String, key: String): String? {
        var result: ByteArray? = null
        try {
            val signinKey = SecretKeySpec(key.toByteArray(), "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(signinKey)
            val rawHmac = mac.doFinal(data.toByteArray())
            result = Base64.getEncoder().encode(rawHmac)
        } catch (e: NoSuchAlgorithmException) {
            System.err.println(e.message)
        } catch (e: InvalidKeyException) {
            System.err.println(e.message)
        }
        return result?.let { String(it) }
    }
    companion object {
        const val USER_ID_KEY = "ownerId"
    }
}
