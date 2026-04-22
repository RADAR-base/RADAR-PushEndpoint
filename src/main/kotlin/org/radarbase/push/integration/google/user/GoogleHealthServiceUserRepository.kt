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

package org.radarbase.push.integration.google.user

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectReader
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.core.Context
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.kafka.common.config.ConfigException
import org.radarbase.exception.TokenException
import org.radarbase.gateway.Config
import org.radarbase.gateway.GoogleHealthConfig
import org.radarbase.oauth.OAuth2Client
import org.radarbase.push.integration.common.inject.ObjectReaderFactory
import org.radarbase.push.integration.common.user.User
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class GoogleHealthServiceUserRepository(
    @Context private val config: Config,
    @Context private val client: OkHttpClient,
    @Context private val objectReaderFactory: ObjectReaderFactory,
) : GoogleHealthUserRepository() {
    private val ghConfig: GoogleHealthConfig = config.pushIntegration.googlehealth
    private val cachedTokens: ConcurrentHashMap<String, CachedAccessToken> = ConcurrentHashMap()
    private var nextFetch = MIN_INSTANT

    private val baseUrl: HttpUrl

    private var timedCachedUsers: List<User> = ArrayList()

    private val repositoryClient: OAuth2Client
    private val tokenUrl: URL
    private val clientId: String
    private val clientSecret: String

    private val userListReader: ObjectReader by lazy { objectReaderFactory.readerFor(GoogleHealthUsers::class) }
    private val userReader: ObjectReader by lazy { objectReaderFactory.readerFor(GoogleHealthUser::class) }
    private val tokenReader: ObjectReader by lazy { objectReaderFactory.readerFor(RestOauth2AccessToken::class) }

    init {
        baseUrl = ghConfig.userRepositoryUrl.toHttpUrl()
        tokenUrl = URL(ghConfig.userRepositoryTokenUrl)
        clientId = ghConfig.userRepositoryClientId
        clientSecret = ghConfig.userRepositoryClientSecret

        if (clientId.isEmpty())
            throw ConfigException("Client ID for GoogleHealth user repository is not set.")

        repositoryClient = OAuth2Client.Builder()
            .credentials(clientId, clientSecret)
            .endpoint(tokenUrl)
            .scopes("SUBJECT.READ", "MEASUREMENT.READ", "SUBJECT.UPDATE", "MEASUREMENT.CREATE")
            .httpClient(client)
            .build()
    }

    @Throws(IOException::class)
    override fun get(key: String): User? {
        val request: Request = requestFor("users/$key").build()
        return makeRequest(request, userReader)
    }

    @Throws(IOException::class)
    override fun stream(): Sequence<User> {
        if (hasPendingUpdates()) {
            applyPendingUpdates()
        }
        return timedCachedUsers.asSequence()
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String = getOAuth2AccessToken(user)

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getRefreshToken(user: User): String {
        throw UnsupportedOperationException("Refresh tokens are managed by RSA, not PEP")
    }

    @Throws(IOException::class)
    override fun getOAuth2AccessToken(user: User): String {
        val cached = cachedTokens[user.id]
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken
        }
        val request = requestFor("users/${user.id}/token").build()
        val token = makeRequest<RestOauth2AccessToken>(request, tokenReader)
        cachedTokens[user.id] = CachedAccessToken(
            accessToken = token.accessToken,
            expiresAt = token.expiresAt ?: Instant.now().plus(TOKEN_CACHE_DURATION),
        )
        return token.accessToken
    }

    override fun findByExternalId(externalId: String): User {
        return stream()
            .firstOrNull { it.externalId == externalId }
            ?: throw NoSuchElementException("GoogleHealth user not found: $externalId")
    }

    override fun deregisterUser(serviceUserId: String) {
        val request =
            requestFor("source-clients/$GOOGLEHEALTH_SOURCE/authorization/$serviceUserId")
                .method("DELETE", EMPTY_BODY).build()
        return makeRequest(request, null)
    }

    override fun hasPendingUpdates(): Boolean {
        val now = Instant.now()
        return now.isAfter(nextFetch)
    }

    @Throws(IOException::class)
    override fun applyPendingUpdates() {
        logger.info("Requesting GoogleHealth user information from webservice")
        val request = requestFor("users?source-type=$GOOGLEHEALTH_SOURCE&authorized=true").build()
        timedCachedUsers = makeRequest<GoogleHealthUsers>(request, userListReader).users

        nextFetch = Instant.now().plus(FETCH_THRESHOLD)
    }

    @Throws(IOException::class)
    private fun requestFor(relativeUrl: String): Request.Builder {
        val url: HttpUrl = baseUrl.resolve(relativeUrl)
            ?: throw IllegalArgumentException("Relative URL is invalid")
        val builder: Request.Builder = Request.Builder().url(url)
        val authorization = requestAuthorization()
        builder.addHeader("Authorization", authorization)

        return builder
    }

    @Throws(IOException::class)
    private fun requestAuthorization(): String {
        return try {
            "Bearer " + repositoryClient.validToken.accessToken
        } catch (ex: TokenException) {
            throw IOException(ex)
        }
    }

    @Throws(IOException::class)
    private fun <T> makeRequest(request: Request, reader: ObjectReader?): T {
        logger.info("Requesting info from {}", request.url)
        client.newCall(request).execute().use { response ->
            val body: ResponseBody? = response.body
            if (response.code == 404) {
                throw NoSuchElementException("URL " + request.url + " does not exist")
            } else if (!response.isSuccessful || body == null) {
                var message = "Failed to make request (HTTP status code " + response.code + ')'
                if (body != null) {
                    message += body.string()
                }
                throw IOException(message)
            }
            val bodyString = body.string()
            @Suppress("UNCHECKED_CAST")
            return try {
                if (reader == null) "" as T
                else reader.readValue(bodyString)
            } catch (ex: JsonProcessingException) {
                logger.error("Failed to parse JSON: {}\n{}", ex.toString(), bodyString)
                throw ex
            }
        }
    }

    private fun String.toHttpUrl(): HttpUrl {
        var urlString: String = this.trim()
        if (urlString[urlString.length - 1] != '/') urlString += '/'

        return urlString.toHttpUrlOrNull()
            ?: throw NoSuchElementException("User repository URL $urlString cannot be parsed as URL.")
    }

    private data class CachedAccessToken(
        val accessToken: String,
        val expiresAt: Instant,
    ) {
        fun isExpired(): Boolean = Instant.now().isAfter(expiresAt.minus(EXPIRY_MARGIN))
    }

    companion object {
        private const val GOOGLEHEALTH_SOURCE = "GoogleHealth"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY: RequestBody = "".toRequestBody(JSON_MEDIA_TYPE)

        private val FETCH_THRESHOLD: Duration = Duration.ofMinutes(1L)
        private val MIN_INSTANT: Instant = Instant.EPOCH
        private val TOKEN_CACHE_DURATION: Duration = Duration.ofMinutes(50L)
        private val EXPIRY_MARGIN: Duration = Duration.ofMinutes(2L)

        private val logger = LoggerFactory.getLogger(GoogleHealthServiceUserRepository::class.java)
    }
}
