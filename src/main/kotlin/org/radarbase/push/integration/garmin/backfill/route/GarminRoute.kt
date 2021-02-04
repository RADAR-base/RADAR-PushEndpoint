package org.radarbase.push.integration.garmin.backfill.route

import okhttp3.HttpUrl
import okhttp3.Request
import org.radarbase.push.integration.common.auth.Oauth1Signing
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_CONSUMER_KEY
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_TOKEN
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_SIGNATURE
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_SIGNATURE_METHOD
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_SIGNATURE_METHOD_VALUE
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_VERSION
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_VERSION_VALUE
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_NONCE
import org.radarbase.push.integration.common.auth.Oauth1Signing.Companion.OAUTH_TIMESTAMP
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
        val parameters = getParams(request.url)
        val oauth1 = Oauth1Signing(parameters)

        val signature = userRepository.getOAuthSignature(user, baseUrl, ROUTE_METHOD, parameters)
        parameters[OAUTH_TOKEN] = accessToken
        parameters[OAUTH_SIGNATURE] = signature.signedUrl

        return oauth1.signRequest(request)
    }

    fun getParams(url: HttpUrl): HashMap<String, String> {
        return (
            mapOf(
                OAUTH_CONSUMER_KEY to consumerKey,
                OAUTH_NONCE to java.util.UUID.randomUUID().toString(),
                OAUTH_SIGNATURE_METHOD to OAUTH_SIGNATURE_METHOD_VALUE,
                OAUTH_TIMESTAMP to (System.currentTimeMillis() / 1000L).toString(),
                OAUTH_VERSION to OAUTH_VERSION_VALUE,
            )
            + (0 until querySize)
                .map { url.queryParameterName(it) to url.queryParameterValue(it) ?: "" }
        )
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
        const val ROUTE_METHOD = "GET"
    }
}
