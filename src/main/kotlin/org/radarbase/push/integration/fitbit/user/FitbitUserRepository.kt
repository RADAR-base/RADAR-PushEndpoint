package org.radarbase.push.integration.fitbit.user

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectReader
import jakarta.ws.rs.core.Context
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.radarbase.exception.TokenException
import org.radarbase.gateway.Config
import org.radarbase.gateway.FitbitConfig
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarbase.oauth.OAuth2Client
import org.radarbase.push.integration.common.inject.ObjectReaderFactory
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class FitbitUserRepository(
    @Context private val config: Config,
    @Context private val client: OkHttpClient,
    @Context private val objectReaderFactory: ObjectReaderFactory,
) : UserRepository {
    private val fitbitConfig: FitbitConfig = config.pushIntegration.fitbit
    private val cachedCredentials: ConcurrentHashMap<String, FitbitUserCredentials> =
        ConcurrentHashMap<String, FitbitUserCredentials>()
    private var nextFetch = MIN_INSTANT

    private val baseUrl: HttpUrl = fitbitConfig.userRepositoryUrl.toHttpUrl()
    private var timedCachedUsers: List<User> = ArrayList()
    private val tokenUrl: URL = URL(fitbitConfig.userRepositoryTokenUrl)
    private val clientId: String = fitbitConfig.userRepositoryClientId
    private val clientSecret: String = fitbitConfig.userRepositoryClientSecret
    private val repositoryClient: OAuth2Client = OAuth2Client.Builder()
        .credentials(clientId, clientSecret)
        .endpoint(tokenUrl)
        .scopes("SUBJECT.READ", "MEASUREMENT.READ", "SUBJECT.UPDATE", "MEASUREMENT.CREATE")
        .httpClient(client)
        .build()

    private val userListReader: ObjectReader by lazy { objectReaderFactory.readerFor(FitbitUsers::class) }
    private val userReader: ObjectReader by lazy { objectReaderFactory.readerFor(FitbitUser::class) }
    private val oauthReader: ObjectReader by lazy {
        objectReaderFactory.readerFor(
            FitbitUserCredentials::class
        )
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

    override fun hasPendingUpdates(): Boolean {
        val now = Instant.now()
        return now.isAfter(nextFetch)
    }

    override fun applyPendingUpdates() {
        logger.info("Requesting user information from webservice")
        val request = requestFor("users?source-type=$FITBIT_SOURCE").build()
        timedCachedUsers = makeRequest<FitbitUsers>(request, userListReader).users

        nextFetch = Instant.now().plus(FETCH_THRESHOLD)
    }

    override fun getAccessToken(user: User): String {
        val credentials: FitbitUserCredentials =
            cachedCredentials[user.id] ?: requestUserCredentials(user)
        return credentials.accessToken
    }

    override fun getRefreshToken(user: User): String {
        throw HttpBadRequestException("", "Not available for source type")
    }


    fun requestUserCredentials(user: User): FitbitUserCredentials {
        val request = requestFor("users/" + user.id + "/token").build()
        val credentials = makeRequest(request, oauthReader) as FitbitUserCredentials
        cachedCredentials[user.id] = credentials
        return credentials
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
            return try {
                if (reader == null) "" as T
                else reader.readValue(bodyString)
            } catch (ex: JsonProcessingException) {
                logger.error("Failed to parse JSON: {}\n{}", ex.toString(), bodyString)
                throw ex
            }
        }
    }

    companion object {
        private const val FITBIT_SOURCE = "FitBit"
        private val FETCH_THRESHOLD: Duration = Duration.ofMinutes(1L)
        private val MIN_INSTANT: Instant = Instant.EPOCH
        private val logger = LoggerFactory.getLogger(FitbitUserRepository::class.java)
    }
}