package org.radarbase.push.integration.garmin.backfill

import org.radarbase.push.integration.common.user.User
import java.time.Duration
import java.time.Instant

interface Route {

    /**
     * The number of days to request in a single request of this route.
     */
    val maxIntervalPerRequest: Duration

    fun generateRequests(user: User, start: Instant, end: Instant, max: Int): Sequence<RestRequest>

    /**
     * This is how it would appear in the offsets
     */
    override fun toString(): String
}
