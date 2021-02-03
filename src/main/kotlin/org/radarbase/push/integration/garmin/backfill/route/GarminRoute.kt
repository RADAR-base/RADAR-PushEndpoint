package org.radarbase.push.integration.garmin.backfill.route

import okhttp3.HttpUrl
import okhttp3.Request
import org.radarbase.push.integration.common.auth.Oauth1Signing
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.backfill.RestRequest
import org.radarbase.push.integration.garmin.backfill.Route
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import java.time.Duration
import java.time.Instant

abstract class GarminRoute(
    private val consumerKey: String,
    private val userRepository: GarminUserRepository
) : Route {
    override val maxDaysPerRequest: Int
        get() = 5

    fun createRequest(user: User, baseUrl: String, queryParams: String): Request {
        val request = Request.Builder()
            .url(baseUrl + queryParams)
            .get()
            .build()

        val accessToken = userRepository.getAccessToken(user)
        val parameters = getParams(request.url, accessToken)
        val oauth1 = Oauth1Signing(parameters)

        val signature = userRepository.getOAuthSignature(user, baseUrl, ROUTE_METHOD, parameters)
        parameters[OAUTH_SIGNATURE] = signature.signedUrl

        return oauth1.signRequest(request)
    }

    fun getParams(url: HttpUrl, accessToken: String): HashMap<String, String> {
        val parameters = hashMapOf(
            OAUTH_CONSUMER_KEY to consumerKey,
            OAUTH_NONCE to java.util.UUID.randomUUID().toString(),
            OAUTH_SIGNATURE_METHOD to OAUTH_SIGNATURE_METHOD_VALUE,
            OAUTH_TIMESTAMP to (System.currentTimeMillis() / 1000L).toString(),
            OAUTH_TOKEN to accessToken,
            OAUTH_VERSION to OAUTH_VERSION_VALUE
        )

        for (i in 0 until url.querySize) {
            parameters[url.queryParameterName(i)] = url.queryParameterValue(i) ?: ""
        }
        return parameters
    }


    override fun generateRequests(
        user: User,
        start: Instant,
        end: Instant,
        max: Int
    ): List<RestRequest> {
        var startRange = Instant.from(start)
        val requests = mutableListOf<RestRequest>()

        while (startRange < end && requests.size < max) {
            val endRange = startRange.plus(Duration.ofDays(maxDaysPerRequest.toLong()))
            val request = createRequest(
                user, "$GARMIN_BACKFILL_BASE_URL/${subPath()}",
                        "?summaryStartTimeInSeconds=${startRange.epochSecond}" +
                        "&summaryEndTimeInSeconds=${endRange.epochSecond}"
            )
            requests.add(RestRequest(request, user, this, startRange, endRange))
            startRange = endRange
        }
        return requests.toMutableList()
    }

    abstract fun subPath(): String

    companion object {

        const val GARMIN_BACKFILL_BASE_URL =
            "https://healthapi.garmin.com/wellness-api/rest/backfill"

        private const val OAUTH_CONSUMER_KEY = "oauth_consumer_key"
        private const val OAUTH_NONCE = "oauth_nonce"
        private const val OAUTH_SIGNATURE = "oauth_signature"
        private const val OAUTH_SIGNATURE_METHOD = "oauth_signature_method"
        private const val OAUTH_SIGNATURE_METHOD_VALUE = "HMAC-SHA1"
        private const val OAUTH_TIMESTAMP = "oauth_timestamp"
        private const val OAUTH_TOKEN = "oauth_token"
        private const val OAUTH_VERSION = "oauth_version"
        private const val OAUTH_VERSION_VALUE = "1.0"
        private const val ROUTE_METHOD = "GET"
    }
}
