package org.radarbase.push.integration.oura.user

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectReader
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.core.Context
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.kafka.common.config.ConfigException
import org.json.JSONObject
import org.radarbase.exception.TokenException
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarbase.oauth.OAuth2Client
import org.radarbase.push.integration.common.auth.SignRequestParams
import org.radarbase.push.integration.common.inject.ObjectReaderFactory
import org.radarbase.oura.user.User
import org.radarbase.push.integration.common.user.Users
import org.radarbase.push.integration.oura.user.OuraUsers
import org.radarbase.oura.user.OuraUser
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.radarbase.push.integration.garmin.user.OAuth1UserCredentials

@Suppress("UNCHECKED_CAST")
class OuraServiceUserRepository(
    @Context private val config: Config,
    @Context private val client: OkHttpClient,
    @Context private val objectReaderFactory: ObjectReaderFactory,
) : OuraUserRepository(config) {
    private val garminConfig: GarminConfig = config.pushIntegration.garmin
    private val cachedCredentials: ConcurrentHashMap<String, OAuth1UserCredentials> =
        ConcurrentHashMap<String, OAuth1UserCredentials>()
    private var nextFetch = MIN_INSTANT

    private val baseUrl: HttpUrl

    private var timedCachedUsers: List<User> = ArrayList()

    private val repositoryClient: OAuth2Client
    private val tokenUrl: URL
    private val clientId: String
    private val clientSecret: String

    private val userListReader: ObjectReader by lazy { objectReaderFactory.readerFor(OuraUsers::class) }
    private val userReader: ObjectReader by lazy { objectReaderFactory.readerFor(OuraUser::class) }
    private val oauthReader: ObjectReader by lazy { objectReaderFactory.readerFor(OAuth1UserCredentials::class) }
    private val signedRequestReader: ObjectReader by lazy { objectReaderFactory.readerFor(SignRequestParams::class) }

    init {
        baseUrl = garminConfig.userRepositoryUrl.toHttpUrl()
        tokenUrl = URL(garminConfig.userRepositoryTokenUrl)
        clientId = garminConfig.userRepositoryClientId
        clientSecret = garminConfig.userRepositoryClientSecret

        if (clientId.isEmpty())
            throw ConfigException("Client ID for user repository is not set.")

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

    fun requestUserCredentials(user: User): OAuth1UserCredentials {
        val request = requestFor("users/" + user.id + "/token").build()
        val credentials = makeRequest(request, oauthReader) as OAuth1UserCredentials
        cachedCredentials[user.id] = credentials
        return credentials
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String {
        val credentials: OAuth1UserCredentials =
            cachedCredentials[user.id] ?: requestUserCredentials(user)
        return credentials.accessToken
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    fun getUserAccessTokenSecret(user: User): String {
        throw HttpBadRequestException("", "Not available for source type")
    }

    fun getSignedRequest(user: User, payload: SignRequestParams): SignRequestParams {
        val body = JSONObject(payload).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = requestFor("users/" + user.id + "/token/sign").method("POST", body).build()

        return makeRequest(request, signedRequestReader)
    }

    override fun deregisterUser(serviceUserId: String, userAccessToken: String) {
        val request =
            requestFor("source-clients/$SOURCE/authorization/$serviceUserId?accessToken=$userAccessToken")
                .method("DELETE", EMPTY_BODY).build()
        return makeRequest(request, null)
    }

    fun findByExternalId(externalId: String): User {
        return stream().first()
    }

    fun hasPendingUpdates(): Boolean {
        val now = Instant.now()
        return now.isAfter(nextFetch)
    }

    @Throws(IOException::class)
    fun applyPendingUpdates() {
        logger.info("Requesting user information from webservice")
        val request = requestFor("users?source-type=$SOURCE&authorized=true").build()
        timedCachedUsers = makeRequest<OuraUsers>(request, userListReader).users
        
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
        logger.info("Requesting auth..")
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

    private fun String.toHttpUrl(): HttpUrl {
        var urlString: String = this.trim()
        if (urlString[urlString.length - 1] != '/') urlString += '/'

        return urlString.toHttpUrlOrNull()
            ?: throw NoSuchElementException("User repository URL $urlString cannot be parsed as URL.")
    }

    companion object {
        private const val SOURCE = "Oura"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY: RequestBody = "".toRequestBody(JSON_MEDIA_TYPE)

        private val FETCH_THRESHOLD: Duration = Duration.ofMinutes(1L)
        private val MIN_INSTANT: Instant = Instant.EPOCH

        private val logger = LoggerFactory.getLogger(OuraServiceUserRepository::class.java)
    }
}
