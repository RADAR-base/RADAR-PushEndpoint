/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Named
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GOOGLE_HEALTH_QUALIFIER
import org.radarbase.push.integration.google.model.GoogleHealthPing
import org.radarbase.push.integration.google.model.PingInterval
import org.radarbase.push.integration.google.user.GoogleHealthUserRepository
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Instant

@Suppress("unused")
class GoogleHealthAuthValidator(
    @param:Context private val objectMapper: ObjectMapper,
    @param:Context private val config: Config,
    @param:Named(GOOGLE_HEALTH_QUALIFIER) @param:Context private val userRepository: GoogleHealthUserRepository,
) : AuthValidator {

    private val expectedSecret: String = config.pushIntegration.googlehealth.subscriberSecret

    override fun getToken(request: ContainerRequestContext): String {
        if (request.hasEntity()) {
            val bodyBytes = request.entityStream.readAllBytes()
            request.setProperty(BODY_BYTES_PROPERTY, bodyBytes)
            request.entityStream = ByteArrayInputStream(bodyBytes)
        }
        return request.getHeaderString("Authorization") ?: ""
    }

    override fun verify(token: String, request: ContainerRequestContext): Auth {
        val userAgent = request.getHeaderString("User-Agent") ?: ""
        val isHandshake = userAgent == VERIFICATION_USER_AGENT
        val bodyBytes = request.getProperty(BODY_BYTES_PROPERTY) as? ByteArray

        if (isHandshake) return handleHandshake(token, request)

        verifyBearer(token)

        // Parse PING
        if (bodyBytes != null && bodyBytes.isNotEmpty()) {
            val tree = objectMapper.readTree(ByteArrayInputStream(bodyBytes))
            request.setProperty("ping_tree", tree)
            parsePing(tree, request)
        }

        return DisabledAuth("res_gateway")
    }

    private fun parsePing(tree: JsonNode, request: ContainerRequestContext) {
        val data = tree["data"] ?: return
        val ping = GoogleHealthPing(
            healthUserId = data["healthUserId"]?.asText() ?: return,
            operation = data["operation"]?.asText() ?: "UPSERT",
            dataType = data["dataType"]?.asText() ?: return,
            intervals = data["intervals"]?.map { i ->
                val p = i["physicalTimeInterval"]
                PingInterval(
                    physicalStartTime = Instant.parse(p["startTime"].asText()),
                    physicalEndTime = Instant.parse(p["endTime"].asText()),
                )
            } ?: emptyList(),
        )
        request.setProperty(PING_PROPERTY, ping)
    }

    private fun handleHandshake(token: String, request: ContainerRequestContext): Auth {
        if (token.isNotEmpty() && constantTimeEquals(token, "Bearer $expectedSecret")) {
            logger.info("Google Health verification handshake: authorized")
            request.setProperty(HANDSHAKE_PROPERTY, true)
            return DisabledAuth("res_gateway")
        }

        logger.info("Google Health verification handshake: unauthorized -- rejecting")
        throw HttpUnauthorizedException(
            "handshake_unauthorized",
            "Verification handshake: missing or invalid authorization",
        )
    }

    private fun verifyBearer(token: String) {
        if (token.isEmpty()) {
            throw HttpUnauthorizedException("missing_token", "Authorization header is missing")
        }
        val expected = "Bearer $expectedSecret"
        if (!constantTimeEquals(token, expected)) {
            logger.warn("Bearer token mismatch")
            throw HttpUnauthorizedException("invalid_token", "Bearer token does not match")
        }
    }

    companion object {
        const val BODY_BYTES_PROPERTY = "googlehealth_body_bytes"
        const val HANDSHAKE_PROPERTY = "googlehealth_handshake"
        const val PING_PROPERTY = "googlehealth_ping"
        private const val VERIFICATION_USER_AGENT = "Google-Health-API-Webhooks-Verifier"
        private val logger = LoggerFactory.getLogger(GoogleHealthAuthValidator::class.java)

        private fun constantTimeEquals(a: String, b: String): Boolean {
            return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
        }
    }
}
