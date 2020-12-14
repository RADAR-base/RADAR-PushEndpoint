package org.radarbase.push.integration.garmin.util.offset

import java.util.*

@Suppress("EqualsOrHashCode")
class UserRoute(val userId: String, val route: String) : Comparable<UserRoute> {
    private val hash = Objects.hash(userId, route)

    override fun hashCode(): Int = hash

    override fun compareTo(other: UserRoute): Int = compareValuesBy(
        this, other,
        UserRoute::userId, UserRoute::route
    )
}
