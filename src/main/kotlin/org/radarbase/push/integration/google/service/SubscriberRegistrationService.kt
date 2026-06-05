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

package org.radarbase.push.integration.google.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.core.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.DESTROY_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.INITIALIZATION_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.push.integration.google.util.GoogleServiceAccountTokenProvider
import org.slf4j.LoggerFactory

/**
 * Registers a Google Health webhook subscriber at application startup.
 */
class SubscriberRegistrationService(
    @param:Context private val config: Config,
    @param:Context private val httpClient: OkHttpClient,
    @param:Context private val objectMapper: ObjectMapper,
    @param:Context private val tokenProvider: GoogleServiceAccountTokenProvider,
) : ApplicationEventListener {

    private val ghConfig = config.pushIntegration.googlehealth
    private val projectId = ghConfig.googleCloudProjectId
    private val subscriberId = ghConfig.subscriberId
    private val baseUrl = ghConfig.apiBaseUrl

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            INITIALIZATION_FINISHED -> registerSubscriber()
            DESTROY_FINISHED -> {
                // Intentionally not unsubscribing on shutdown. Keeping the subscription alive across
                // restarts avoids a window where PINGs would be missed. If operators need to remove the subscriber.
                logger.info("Application shutting down — subscriber {} left active", subscriberId)
            }
            else -> { /* no-op */ }
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun registerSubscriber() {
        if (!tokenProvider.isConfigured) {
            logger.warn(
                "Service account not configured -- skipping subscriber registration. " +
                    "Set googlehealth.serviceAccountKeyPath to enable."
            )
            return
        }
        try {
            val accessToken = tokenProvider.getAccessToken()
            val existing = getSubscriber(accessToken)
            if (existing == null) {
                createSubscriber(accessToken)
            } else {
                maybeUpdateSubscriber(existing, accessToken)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to register subscriber {} — PINGs will not be received until " +
                    "the issue is resolved and PEP is restarted: {}",
                subscriberId, e.message, e,
            )
        }
    }

    private fun getSubscriber(accessToken: String): Map<*, *>? {
        val url = "$baseUrl/projects/$projectId/subscribers/$subscriberId"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return null
                    @Suppress("UNCHECKED_CAST")
                    objectMapper.readValue(body, Map::class.java)
                }
                404 -> null
                else -> {
                    val errBody = response.body?.string()
                    logger.warn("GET subscriber returned {}: {}", response.code, errBody)
                    null
                }
            }
        }
    }

    private fun createSubscriber(accessToken: String) {
        logger.info("Creating subscriber {} for project {}", subscriberId, projectId)
        val url = "$baseUrl/projects/$projectId/subscribers?subscriberId=$subscriberId"
        val payload = buildSubscriberPayload()
        val body = objectMapper.writeValueAsString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val respBody = response.body?.string()
            if (response.isSuccessful) {
                logger.info("Subscriber(ID: {}) created. Listening on {}", subscriberId, ghConfig.subscriberEndpointUri)
            } else {
                logger.error(
                    "Failed to create subscriber {}: HTTP {} - {}. " +
                        "If subscriberEndpointUri ({}) does not match the live deployment, " +
                        "Google will reject the verification handshake.",
                    subscriberId, response.code, respBody, ghConfig.subscriberEndpointUri,
                )
            }
        }
    }

    private fun maybeUpdateSubscriber(existing: Map<*, *>, accessToken: String) {
        val existingUri = existing["endpointUri"] as? String
        val desiredUri = ghConfig.subscriberEndpointUri

        @Suppress("UNCHECKED_CAST")
        val existingConfigs = existing["subscriberConfigs"] as? List<Map<String, Any>>
        val desiredConfigs = buildSubscriberConfigs()

        val needsUpdate = existingUri != desiredUri || !configsMatch(existingConfigs, desiredConfigs)

        if (!needsUpdate) {
            logger.info("Subscriber {} already exists and is up-to-date — state ACTIVE", subscriberId)
            return
        }

        logger.info("Subscriber {} exists but needs update — patching", subscriberId)
        val url = "$baseUrl/projects/$projectId/subscribers/$subscriberId" +
            "?updateMask=endpointUri,subscriberConfigs"
        val payload = mapOf(
            "endpointUri" to desiredUri,
            "subscriberConfigs" to desiredConfigs,
        )
        val body = objectMapper.writeValueAsString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .patch(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val respBody = response.body?.string()
            if (response.isSuccessful) {
                logger.info("Subscriber {} patched successfully — state ACTIVE", subscriberId)
            } else {
                logger.error(
                    "Failed to patch subscriber {}: HTTP {} — {}",
                    subscriberId, response.code, respBody,
                )
            }
        }
    }

    private fun buildSubscriberPayload(): Map<String, Any> = mapOf(
        "endpointUri" to ghConfig.subscriberEndpointUri,
        "subscriberConfigs" to buildSubscriberConfigs(),
        "endpointAuthorization" to mapOf(
            "secret" to "Bearer ${ghConfig.subscriberSecret}",
            "secretSet" to false,
        ),
    )

    private fun buildSubscriberConfigs(): List<Map<String, Any>> = listOf(
        mapOf(
            "dataTypes" to ghConfig.triggerDataTypes,
            "subscriptionCreatePolicy" to "AUTOMATIC",
        )
    )

    private fun configsMatch(
        existing: List<Map<String, Any>>?,
        desired: List<Map<String, Any>>,
    ): Boolean {
        if (existing == null || existing.size != desired.size) return false
        val existingDataTypes = existing.firstOrNull()?.get("dataTypes")
        val desiredDataTypes = desired.firstOrNull()?.get("dataTypes")
        return existingDataTypes == desiredDataTypes
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val logger = LoggerFactory.getLogger(SubscriberRegistrationService::class.java)
    }
}
