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
import org.radarbase.googlehealth.model.GoogleHealthPing
import org.radarbase.googlehealth.model.PingInterval
import org.radarbase.googlehealth.user.GoogleHealthUserRepository
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Instant

@Suppress("unused")
class GoogleHealthAuthValidator(
    @param:Context private val objectMapper: ObjectMapper,
    @param:Context private val config: Config,
    @param:Context private val signatureVerifier: GoogleHealthWebhookSignatureVerifier,
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
        val bodyBytes = request.getProperty(BODY_BYTES_PROPERTY) as? ByteArray

        val tree: JsonNode? = if (bodyBytes != null && bodyBytes.isNotEmpty()) {
            try {
                objectMapper.readTree(ByteArrayInputStream(bodyBytes))
            } catch (ex: Exception) {
                logger.warn("Could not parse Google Health request body as JSON (userAgent={})", userAgent, ex)
                null
            }
        } else {
            null
        }

        if (isVerificationHandshake(tree)) {
            logger.info("GH-TEST Google Health verification handshake received (userAgent={})", userAgent)
            return handleHandshake(token, request)
        }

        // Not a handshake => a data push. Authenticate before doing any work on the payload: both the
        // shared bearer secret and Google's cryptographic webhook signature must check out.
        verifyBearer(token)
        signatureVerifier.verify(
            request.getHeaderString(GoogleHealthWebhookSignatureVerifier.SIGNATURE_HEADER),
            bodyBytes ?: ByteArray(0),
        )

        if (tree != null) {
            val pings = parsePings(tree)
            if (pings.isNotEmpty()) request.setProperty(PING_PROPERTY, pings)
        }

        return DisabledAuth("res_gateway")
    }

    private fun isVerificationHandshake(tree: JsonNode?): Boolean =
        tree != null && tree.isObject && tree["type"]?.asText() == VERIFICATION_TYPE

    /**
     * Parse a data-push body into pings. Google Health pushes arrive as a JSON array of
     * notification objects, each shaped `{ "data": { ... } }`.
     */
    private fun parsePings(tree: JsonNode): List<GoogleHealthPing> {
        logger.info("[GH-TEST] Raw Google Health payload received: {}", tree.toPrettyString())

        if (!tree.isArray) {
            logger.warn("Expected a JSON array of Google Health notifications but got {}", tree.nodeType)
            return emptyList()
        }

        val pings = tree.mapNotNull(::parseSinglePing)
        logger.info(
            "Parsed {} Google Health ping(s) from a payload of {} notification element(s)",
            pings.size,
            tree.size(),
        )
        return pings
    }

    private fun parseSinglePing(element: JsonNode): GoogleHealthPing? {
        val data = element["data"] ?: element

        val healthUserId = data["healthUserId"]?.asText()
        if (healthUserId.isNullOrEmpty()) {
            logger.warn("Skipping Google Health notification without healthUserId: {}", element)
            return null
        }

        val operation = data["operation"]?.asText() ?: "UPSERT"
        val dataType = data["dataType"]?.asText() ?: "UNKNOWN"
        val rawIntervals = data["intervals"]
        val intervals = rawIntervals?.mapNotNull(::parseInterval) ?: emptyList()
        val skipped = (rawIntervals?.size() ?: 0) - intervals.size
        if (skipped > 0) {
            logger.info(
                "[GH-TEST] dataType={} healthUserId={}: {} of {} interval(s) had no physicalTimeInterval " +
                    "(civil-only) and were skipped",
                dataType, healthUserId, skipped, rawIntervals?.size() ?: 0,
            )
        }

        return GoogleHealthPing(
            healthUserId = healthUserId,
            operation = operation,
            dataType = dataType,
            intervals = intervals,
        )
    }

    /**
     * Resolve a single interval to its physical (UTC) window. Civil-only intervals (e.g. the
     * daily-aggregate notifications that omit `physicalTimeInterval`) are skipped: their times are
     * the device's local, zoneless times, and the same change also arrives with a physical interval
     * and is covered by the backfill / catch-up paths.
     */
    private fun parseInterval(node: JsonNode): PingInterval? {
        val physical = node["physicalTimeInterval"]
        val start = physical?.get("startTime")?.asText()
        val end = physical?.get("endTime")?.asText()
        if (start.isNullOrEmpty() || end.isNullOrEmpty()) {
            logger.info("[GH-TEST] Skipping Google Health interval without physicalTimeInterval: {}", node)
            return null
        }
        return PingInterval(Instant.parse(start), Instant.parse(end))
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
        // Holds the parsed List<GoogleHealthPing> for a data-push request.
        const val PING_PROPERTY = "googlehealth_ping"
        // Body marker that identifies the subscription verification handshake.
        private const val VERIFICATION_TYPE = "verification"
        private val logger = LoggerFactory.getLogger(GoogleHealthAuthValidator::class.java)

        private fun constantTimeEquals(a: String, b: String): Boolean {
            return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
        }
    }
}
