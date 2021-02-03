package org.radarbase.push.integration.garmin.user

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.confluent.common.config.ConfigException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarbase.push.integration.common.auth.OAuthSignature
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.Users
import org.radarcns.exception.TokenException
import org.radarcns.oauth.OAuth2Client
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context

class GarminServiceUserRepository(
    @Context private val config: Config
) : GarminUserRepository(config) {
    private val garminConfig: GarminConfig = config.pushIntegration.garmin
    private val client: OkHttpClient = OkHttpClient()
    private val cachedCredentials: ConcurrentHashMap<String, OAuth1UserCredentials> = ConcurrentHashMap<String, OAuth1UserCredentials>()
    private var nextFetch = MIN_INSTANT

    private var baseUrl: HttpUrl
    private var timedCachedUsers: List<User> = ArrayList<User>()

    private lateinit var repositoryClient: OAuth2Client
    private var basicCredentials: String? = null
    private var tokenUrl: URL
    private var clientId: String
    private var clientSecret: String

    init {
        baseUrl = garminConfig.userRepositoryUrl.toHttpUrl()
        tokenUrl = URL(garminConfig.userRepositoryTokenUrl)
        clientId = garminConfig.userRepositoryClientId
        clientSecret = garminConfig.userRepositoryClientSecret

        if (tokenUrl != null && clientId.isEmpty())
            throw ConfigException("Client ID for user repository is not set.")

        repositoryClient = if (tokenUrl != null) {
            OAuth2Client.Builder()
                .credentials(clientId, clientSecret)
                .endpoint(tokenUrl)
                .scopes("SUBJECT.READ", "MEASUREMENT.READ", "SUBJECT.UPDATE")
                .httpClient(client)
                .build()
        } else OAuth2Client.Builder().build()

        basicCredentials = if (tokenUrl == null && clientId != null) {
            Credentials.basic(clientId, clientSecret)
        } else null
    }

    @Throws(IOException::class)
    override fun get(key: String): User? {
        val request: Request = requestFor("users/$key").build()
        return makeRequest(request, USER_READER)
    }

    @Throws(IOException::class)
    override fun stream(): Stream<User> {
        if (hasPendingUpdates()) {
            applyPendingUpdates()
        }
        return timedCachedUsers.stream()
    }

    fun requestUserCredentials(user: User): OAuth1UserCredentials {
        val request = requestFor("users/" + user.id + "/token").build()
        val credentials = makeRequest(request, OAUTH_READER) as OAuth1UserCredentials
        cachedCredentials?.set(user.id, credentials)
        return credentials
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String {
        val credentials: OAuth1UserCredentials = cachedCredentials[user.id] ?: requestUserCredentials(user)
        return credentials.accessToken
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getUserAccessTokenSecret(user: User): String {
        throw HttpBadRequestException("", "Not available for source type")
    }

    override fun getOAuthSignature(user: User, url: String, method: String, params: Map<String, String>): OAuthSignature {
        val res = JSONObject().put("url", url).put("method", method).put("params", params).toString()
        val body = res.toRequestBody(JSON_MEDIA_TYPE)
        val request = requestFor("users/" + user.id + "/token/sign").method("POST", body).build()
        return makeRequest(request, SIGNATURE_READER)
    }

    @Throws(IOException::class)
    override fun reportDeregistration(user: User) {
        val request = requestFor("users/" + user.id + "/deregister").method("POST", EMPTY_BODY).build()
        return makeRequest(request, null)
    }

    override fun findByExternalId(externalId: String): User {
        return super.findByExternalId(externalId)
    }

    override fun hasPendingUpdates(): Boolean {
        val now = Instant.now()
        return now.isAfter(nextFetch)
    }

    @Throws(IOException::class)
    override fun applyPendingUpdates() {
        logger.info("Requesting user information from webservice")
        val request = requestFor("users?source-type=Garmin").build()
        timedCachedUsers = makeRequest<Users>(request, USER_LIST_READER).users

        nextFetch = Instant.now().plus(FETCH_THRESHOLD)
    }

    @Throws(IOException::class)
    private fun requestFor(relativeUrl: String): Request.Builder {
        val url: HttpUrl = baseUrl.resolve(relativeUrl)
                ?: throw IllegalArgumentException("Relative URL is invalid")
        val builder: Request.Builder = Request.Builder().url(url)
        val authorization = requestAuthorization()
        if (authorization != null) {
            builder.addHeader("Authorization", authorization)
        }

        return builder
    }

    @Throws(IOException::class)
    private fun requestAuthorization(): String? {
        return when {
            repositoryClient != null -> {
                try {
                    "Bearer " + repositoryClient.validToken.accessToken
                } catch (ex: TokenException) {
                    throw IOException(ex)
                }
            }
            basicCredentials != null -> basicCredentials
            else -> null
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
        private val JSON_FACTORY = JsonFactory()
        private val JSON_READER: ObjectReader = ObjectMapper(JSON_FACTORY).registerModule(JavaTimeModule()).reader()
        private val USER_LIST_READER: ObjectReader = JSON_READER.forType(Users::class.java)
        private val USER_READER: ObjectReader = JSON_READER.forType(GarminUser::class.java)
        private val OAUTH_READER: ObjectReader = JSON_READER.forType(OAuth1UserCredentials::class.java)
        private val SIGNATURE_READER: ObjectReader = JSON_READER.forType(OAuthSignature::class.java)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY: RequestBody = "".toRequestBody(JSON_MEDIA_TYPE)
        private val FETCH_THRESHOLD: Duration = Duration.ofMinutes(1L)
        val MIN_INSTANT = Instant.EPOCH


        private val logger = LoggerFactory.getLogger(GarminServiceUserRepository::class.java)
    }
}
