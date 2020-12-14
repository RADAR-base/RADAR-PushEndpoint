package org.radarbase.push.integration.garmin.backfill

import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import java.time.Duration
import java.time.Instant

class GarminActivitiesRoute(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

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
                user, "${GARMIN_BACKFILL_BASE_URL}/activities" +
                        "?summaryStartTimeInSeconds=${startRange.epochSecond}" +
                        "&summaryEndTimeInSeconds=${endRange.epochSecond}"
            )
            requests.add(RestRequest(request, user, this, startRange, endRange))
            startRange = endRange
        }
        return requests.toMutableList()
    }

    override fun toString(): String = "garmin_activities"
}
