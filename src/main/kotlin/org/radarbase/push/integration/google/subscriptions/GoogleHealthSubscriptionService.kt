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

package org.radarbase.push.integration.google.subscriptions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.core.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.radarbase.gateway.Config
import org.radarbase.push.integration.google.subscriptions.model.RemoteSubscription
import org.radarbase.push.integration.google.subscriptions.model.SubscriptionResult
import org.radarbase.push.integration.google.util.GoogleServiceAccountTokenProvider
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.MessageDigest

/**
 * Manages per-user Google Health subscriptions for this deployment's subscriber
 */
class GoogleHealthSubscriptionService(
    @param:Context private val config: Config,
    @param:Context private val httpClient: OkHttpClient,
    @param:Context private val objectMapper: ObjectMapper,
    @param:Context private val tokenProvider: GoogleServiceAccountTokenProvider,
) {
    private val ghConfig = config.pushIntegration.googlehealth
    private val projectId = ghConfig.googleCloudProjectId
    private val subscriberId = ghConfig.subscriberId
    private val baseUrl = ghConfig.apiBaseUrl.trimEnd('/')
    private val defaultDataTypes = ghConfig.triggerDataTypes

    val isConfigured: Boolean
        get() = tokenProvider.isConfigured

    fun createSubscription(
        healthUserId: String,
        dataTypes: List<String> = defaultDataTypes,
    ): SubscriptionResult {
        val subscriptionId = subscriptionIdFor(healthUserId)
        val url = "$baseUrl/projects/$projectId/subscribers/$subscriberId/subscriptions?subscriptionId=$subscriptionId"
        val payload = mapOf(
            "user" to userResourceName(healthUserId),
            "dataTypes" to dataTypes,
        )
        val body = objectMapper.writeValueAsString(payload).toRequestBody(JSON_MEDIA_TYPE)
        return execute(healthUserId, "create") { token ->
            Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
        }
    }

    fun deleteSubscription(healthUserId: String): SubscriptionResult {
        val subscriptionId = subscriptionIdFor(healthUserId)
        val url = "$baseUrl/projects/$projectId/subscribers/$subscriberId/subscriptions/$subscriptionId"
        return execute(healthUserId, "delete") { token ->
            Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $token")
                .build()
        }
    }

    /**
     * Deletes a subscription by its full Google resource name (as returned by [listSubscriptions]).
     * Reconciliation uses this so it removes exactly what Google reported, independent of how the
     * subscription's id was generated (e.g. a leftover AUTOMATIC or out-of-band subscription).
     * Idempotent: a missing subscription reports [SubscriptionResult.Success].
     */
    fun deleteByName(name: String): SubscriptionResult {
        val url = "$baseUrl/$name"
        return execute(name, "delete") { token ->
            Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $token")
                .build()
        }
    }

    /**
     * Patches a subscription's data types. Google stores and returns data types as fully-qualified
     * resource names (`users/{userId}/dataTypes/{type}`), and the patch body must send them in that
     * same form (along with the resource [name] and [user]).
     *
     * @param user the subscription's user resource name, i.e. `users/{userId}`.
     * @param dataTypes bare data-type tokens (e.g. "steps"); qualified here against [user].
     */
    fun patchSubscription(name: String, user: String, dataTypes: List<String>): SubscriptionResult {
        val url = "$baseUrl/$name?updateMask=dataTypes"
        val payload = mapOf(
            "name" to name,
            "user" to user,
            "dataTypes" to dataTypes.map { "$user/dataTypes/$it" },
        )
        val body = objectMapper.writeValueAsString(payload).toRequestBody(JSON_MEDIA_TYPE)
        return execute(name, "patch") { token ->
            Request.Builder()
                .url(url)
                .patch(body)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
        }
    }

    /**
     * Returns every subscription under this deployment's subscriber (following all pages).
     *
     * Throws on any error instead of returning an empty list, so reconcile treats a failed read as
     * "unknown" and skips the pass — a hiccup is never mistaken for "Google has no subscriptions".
     */
    @Throws(IOException::class)
    fun listSubscriptions(): List<RemoteSubscription> {
        check(tokenProvider.isConfigured) { "Service account not configured" }
        val token = tokenProvider.getAccessToken()
        val result = mutableListOf<RemoteSubscription>()
        var pageToken: String? = null
        do {
            val builder = "$baseUrl/projects/$projectId/subscribers/$subscriberId/subscriptions"
                .toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
            if (!pageToken.isNullOrEmpty()) builder.addQueryParameter("pageToken", pageToken)
            val request = Request.Builder()
                .url(builder.build())
                .get()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw IOException(
                        "List subscriptions failed for subscriber $subscriberId: HTTP ${response.code} - $respBody",
                    )
                }
                val tree = if (respBody.isNullOrEmpty()) {
                    objectMapper.createObjectNode()
                } else {
                    objectMapper.readTree(respBody)
                }
                tree["subscriptions"]?.forEach { node -> result += parseSubscription(node) }
                pageToken = tree["nextPageToken"]?.asText()?.takeIf { it.isNotEmpty() }
            }
        } while (pageToken != null)
        return result
    }

    private fun execute(
        identifier: String,
        action: String,
        buildRequest: (String) -> Request,
    ): SubscriptionResult {
        if (!tokenProvider.isConfigured) return SubscriptionResult.NotConfigured
        val token = try {
            tokenProvider.getAccessToken()
        } catch (ex: Exception) {
            logger.warn("Could not obtain service-account token for {} of {}", action, identifier, ex)
            return SubscriptionResult.TransientFailure("token: ${ex.message}")
        }
        return try {
            httpClient.newCall(buildRequest(token)).execute().use { response ->
                val respBody = response.body?.string()
                when {
                    response.isSuccessful -> {
                        val name = respBody?.let {
                            runCatching {
                                objectMapper.readTree(it)["name"]?.asText()
                            }.getOrNull()
                        }
                        logger.info("Subscription {} ok for {} (name={})", action, identifier, name)
                        SubscriptionResult.Success(name)
                    }
                    // create: subscription already present. delete: subscription already gone.
                    response.code == 409 || response.code == 404 -> SubscriptionResult.Success(null)

                    response.code == 429 || response.code in 500..599 -> {
                        logger.warn(
                            "Transient {} failure for {}: HTTP {} - {}",
                            action,
                            identifier,
                            response.code,
                            respBody
                        )
                        SubscriptionResult.TransientFailure("HTTP ${response.code}")
                    }

                    else -> {
                        logger.error(
                            "Permanent {} failure for {}: HTTP {} - {}",
                            action,
                            identifier,
                            response.code,
                            respBody
                        )
                        SubscriptionResult.PermanentFailure(response.code, respBody)
                    }
                }
            }
        } catch (ex: IOException) {
            logger.warn("I/O error during subscription {} for {}", action, identifier, ex)
            SubscriptionResult.TransientFailure("io: ${ex.message}")
        }
    }

    private fun parseSubscription(node: JsonNode): RemoteSubscription = RemoteSubscription(
        name = node["name"]?.asText() ?: "",
        user = node["user"]?.asText() ?: "",
        dataTypes = node["dataTypes"]?.mapNotNull { it.asText() } ?: emptyList(),
    )

    private fun userResourceName(healthUserId: String): String = "users/$healthUserId"

    /**
     * Subscription id derived from the health user id. We hash rather than use the raw id because the health user id's
     * character set and length are not guaranteed to satisfy Google's resource-id rules.
     */
    private fun subscriptionIdFor(healthUserId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(healthUserId.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "u" + hex.take(SUBSCRIPTION_ID_HEX_LEN)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val PAGE_SIZE = 1000
        private const val SUBSCRIPTION_ID_HEX_LEN = 32
        private val logger = LoggerFactory.getLogger(GoogleHealthSubscriptionService::class.java)
    }
}
