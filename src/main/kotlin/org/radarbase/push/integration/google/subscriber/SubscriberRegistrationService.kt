/**
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

package org.radarbase.push.integration.google.subscriber

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.core.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.push.integration.google.util.GoogleServiceAccountTokenProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.get

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
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            ApplicationEvent.Type.INITIALIZATION_FINISHED -> scheduleRegistration()
            ApplicationEvent.Type.DESTROY_FINISHED -> {
                scheduler.shutdownNow()
                // Intentionally not unsubscribing on shutdown. Keeping the subscription alive across
                // restarts avoids a window where PINGs would be missed.
                logger.info("Application shutting down — subscriber {} left active", subscriberId)
            }
            else -> { /* no-op */ }
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun scheduleRegistration() {
        if (!tokenProvider.isConfigured) {
            logger.warn(
                "Service account not configured -- skipping subscriber registration. " +
                    "Set googlehealth.serviceAccountKeyPath to enable."
            )
            return
        }
        logger.info(
            "Scheduling subscriber {} registration in {}s (waiting for the HTTP server to accept requests).",
            subscriberId, REGISTRATION_INITIAL_DELAY_SECONDS,
        )
        scheduler.schedule(::registerWithRetry, REGISTRATION_INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * Ensures the subscriber exists, retrying a few times. The first attempt can fail because Google's
     * create-time verification handshake reaches us before the server is fully ready,
     * so we retry with a fixed delay rather than give up after one shot.
     */
    private fun registerWithRetry() {
        for (attempt in 1..MAX_REGISTRATION_ATTEMPTS) {
            val ok = try {
                ensureSubscriber()
            } catch (ex: Exception) {
                logger.warn(
                    "Subscriber {} registration attempt {}/{} errored",
                    subscriberId, attempt, MAX_REGISTRATION_ATTEMPTS, ex,
                )
                false
            }
            if (ok) {
                logger.info("Subscriber {} ensured (attempt {}/{})", subscriberId, attempt, MAX_REGISTRATION_ATTEMPTS)
                return
            }
            if (attempt < MAX_REGISTRATION_ATTEMPTS) {
                try {
                    TimeUnit.SECONDS.sleep(REGISTRATION_RETRY_DELAY_SECONDS)
                } catch (ex: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
        logger.error(
            "Subscriber {} could not be ensured after {} attempts. PINGs will not be received until the issue is" +
                " resolved and the Push Endpoint is restarted.",
            subscriberId, MAX_REGISTRATION_ATTEMPTS,
        )
    }

    /** Creates or updates the subscriber. Returns true when it is in the desired state. */
    private fun ensureSubscriber(): Boolean {
        val accessToken = tokenProvider.getAccessToken()
        val existing = getSubscriber(accessToken)
        return if (existing == null) {
            createSubscriber(accessToken)
        } else {
            maybeUpdateSubscriber(existing, accessToken)
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

    private fun createSubscriber(accessToken: String): Boolean {
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
                return true
            }
            logger.error(
                "Failed to create subscriber {}: HTTP {} - {}. " +
                    "If subscriberEndpointUri ({}) does not match the live deployment, " +
                    "Google will reject the verification handshake.",
                subscriberId, response.code, respBody, ghConfig.subscriberEndpointUri,
            )
            return false
        }
    }

    private fun maybeUpdateSubscriber(existing: Map<*, *>, accessToken: String): Boolean {
        val existingUri = existing["endpointUri"] as? String
        val desiredUri = ghConfig.subscriberEndpointUri

        @Suppress("UNCHECKED_CAST")
        val existingConfigs = existing["subscriberConfigs"] as? List<Map<String, Any>>
        val desiredConfigs = buildSubscriberConfigs()

        val needsUpdate = existingUri != desiredUri || !configsMatch(existingConfigs, desiredConfigs)

        if (!needsUpdate) {
            logger.info("Subscriber {} already exists and is up-to-date — state ACTIVE", subscriberId)
            return true
        }

        logger.info("Subscriber {} exists but needs update", subscriberId)
        val url = "$baseUrl/projects/$projectId/subscribers/$subscriberId?updateMask=endpointUri,subscriberConfigs"
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
                logger.info("Subscriber {} patched successfully", subscriberId)
                return true
            }
            logger.error(
                "Failed to patch subscriber {}: HTTP {} — {}",
                subscriberId, response.code, respBody,
            )
            return false
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
            "subscriptionCreatePolicy" to ghConfig.subscriptionCreatePolicy.name,
        )
    )

    private fun configsMatch(
        existing: List<Map<String, Any>>?,
        desired: List<Map<String, Any>>,
    ): Boolean {
        if (existing == null || existing.size != desired.size) return false
        val existingConfig = existing.firstOrNull()
        val desiredConfig = desired.firstOrNull()
        return existingConfig?.get("dataTypes") == desiredConfig?.get("dataTypes") &&
            existingConfig?.get("subscriptionCreatePolicy") == desiredConfig?.get("subscriptionCreatePolicy")
    }

    companion object {
        private const val REGISTRATION_INITIAL_DELAY_SECONDS = 60L
        private const val REGISTRATION_RETRY_DELAY_SECONDS = 30L
        private const val MAX_REGISTRATION_ATTEMPTS = 3
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val logger = LoggerFactory.getLogger(SubscriberRegistrationService::class.java)
    }
}
