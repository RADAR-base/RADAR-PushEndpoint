package org.radarbase.push.integration.garmin.backfill

import okhttp3.Request
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.auth.Oauth1Signing
import org.radarbase.push.integration.common.auth.OauthKeys
import org.radarbase.push.integration.garmin.user.GarminUserRepository

abstract class GarminRoute(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val userRepository: GarminUserRepository
) : Route {
    override val maxDaysPerRequest: Int
        get() = 1

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

    companion object {

        const val GARMIN_BACKFILL_BASE_URL =
            "https://healthapi.garmin.com/wellness-api/rest/backfill"
    }
}
