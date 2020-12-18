package org.radarbase.push.integration.garmin.backfill.route

import okhttp3.Request
import org.radarbase.push.integration.common.auth.Oauth1Signing
import org.radarbase.push.integration.common.auth.OauthKeys
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.backfill.RestRequest
import org.radarbase.push.integration.garmin.backfill.Route
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import java.time.Duration
import java.time.Instant

abstract class GarminRoute(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val userRepository: GarminUserRepository
) : Route {
    override val maxDaysPerRequest: Int
        get() = 5

    fun createRequest(user: User, url: String): Request {

        val oauth1 = Oauth1Signing(
            oauthKeys = OauthKeys(
                consumerKey,
                consumerSecret,
                userRepository.getAccessToken(user),
                userRepository.getUserAccessTokenSecret(user)
            )
        )

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return oauth1.signRequest(request)
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
                user, "$GARMIN_BACKFILL_BASE_URL/${subPath()}" +
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
    }
}
