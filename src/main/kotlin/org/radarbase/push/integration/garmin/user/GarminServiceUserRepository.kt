package org.radarbase.push.integration.garmin.user

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.Users
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context
import kotlin.collections.HashMap


class GarminServiceUserRepository(
        @Context private val config: Config
) : GarminUserRepository(config) {
    private val garminConfig: GarminConfig = config.pushIntegration.garmin
    private val client: OkHttpClient? = OkHttpClient()
    private val cachedCredentials: HashMap<String, OAuth1UserCredentials>? = HashMap<String, OAuth1UserCredentials>()
    private var nextFetch = MIN_INSTANT

    private val baseUrl: HttpUrl = garminConfig.userRepositoryUrl.toHttpUrl()
    private var timedCachedUsers: List<User?> = ArrayList<User?>()

    // TODO: Index by externalId for quick lookup
    private val cachedMap: Map<String, User> = mapOf(
            "1" to GarminUser(
                    id = "1",
                    projectId = "test",
                    userId = "test",
                    sourceId = "test",
                    externalUserId = "a0015b7d-8904-40d7-8852-815cb7ad7a0b",
                    isAuthorized = true,
                    startDate = Instant.ofEpochSecond(1589548126),
                    endDate = Instant.ofEpochSecond(1590844126),
                    createdAt = Instant.ofEpochSecond(1589548126),
            )
    )

    @Throws(IOException::class)
//    override fun get(key: String): User? = cachedMap[key]
    override fun get(key: String): User? {
        val request: Request = requestFor("users/$key")!!.build()
        return makeRequest(request, USER_READER)
    }

    @Throws(IOException::class)
    override fun stream(): Stream<User?> {
        if (hasPendingUpdates()) {
            applyPendingUpdates()
        }
        return timedCachedUsers.stream()
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String {
        var credentials: OAuth1UserCredentials? = cachedCredentials!![user.id]
        if (credentials == null) {
            val request = requestFor("users/" + user.id + "/token")!!.build()
            credentials =  makeRequest(request, OAUTH_READER) as OAuth1UserCredentials
            cachedCredentials!![user.id] = credentials
        }
        return credentials!!.accessToken
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getUserAccessTokenSecret(user: User): String {
        var credentials: OAuth1UserCredentials? = cachedCredentials!![user.id]
        if (credentials == null) {
            val request = requestFor("users/" + user.id + "/token")!!.build()
            credentials = makeRequest(request, OAUTH_READER) as OAuth1UserCredentials
            cachedCredentials!![user.id] = credentials
        }
        return credentials.accessTokenSecret
    }

    @Throws(IOException::class)
    override fun reportDeregistration(user: User) {
        val request = requestFor("users/" + user.id + "/deregister")!!.build()
        return makeRequest(request, null)
    }

    override fun findByExternalId(externalId: String): User {
        return super.findByExternalId(externalId)!!
    }

    override fun hasPendingUpdates(): Boolean {
        val now = Instant.now()
        return now.isAfter(nextFetch)
    }

    @Throws(IOException::class)
    override fun applyPendingUpdates() {
        logger.info("Requesting user information from webservice")
        val request = requestFor("users?source-type=Garmin")!!.build()
        timedCachedUsers = makeRequest<Users>(request, USER_LIST_READER).getUsers()

        nextFetch = Instant.now().plus(FETCH_THRESHOLD)
    }

    private fun requestFor(relativeUrl: String): Request.Builder? {
        val url: HttpUrl = baseUrl.resolve(relativeUrl)
                ?: throw IllegalArgumentException("Relative URL is invalid")
        return Request.Builder().url(url)
    }

    @Throws(IOException::class)
    private fun <T> makeRequest(request: Request, reader: ObjectReader?): T {
        logger.info("Requesting info from {}", request.url)
        client!!.newCall(request).execute().use { response ->
            val body: ResponseBody? = response.body
            if (response.code === 404) {
                throw NoSuchElementException("URL " + request.url + " does not exist")
            } else if (!response.isSuccessful || body == null) {
                var message = "Failed to make request"
                if (response.code > 0) {
                    message += " (HTTP status code " + response.code + ')'
                }
                if (body != null) {
                    message += body.string()
                }
                throw IOException(message)
            }
            val bodyString = body.string()
            return try {
                reader!!.readValue(bodyString)
            } catch (ex: JsonProcessingException) {
                logger.error("Failed to parse JSON: {}\n{}", ex.toString(), bodyString)
                throw ex
            }
        }
    }

    private fun String.toHttpUrl(): HttpUrl {
        var urlString: String = this.trim()
        if (urlString[urlString.length - 1] != '/') {
            urlString += '/'
        }
        return urlString.toHttpUrlOrNull() ?: throw NoSuchElementException("User repository URL $urlString cannot be parsed as URL.")
    }

    companion object {
        private val JSON_FACTORY = JsonFactory()
        private val JSON_READER: ObjectReader = ObjectMapper(JSON_FACTORY).registerModule(JavaTimeModule()).reader()
        private val USER_LIST_READER: ObjectReader = JSON_READER.forType(Users::class.java)
        private val USER_READER: ObjectReader = JSON_READER.forType(GarminUser::class.java)
        private val OAUTH_READER: ObjectReader = JSON_READER.forType(OAuth1UserCredentials::class.java)
        private val EMPTY_BODY: RequestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), "")
        private val FETCH_THRESHOLD: Duration = Duration.ofMinutes(1L)
        var MIN_INSTANT = Instant.EPOCH


        private val logger = LoggerFactory.getLogger(GarminServiceUserRepository::class.java)
    }
}
