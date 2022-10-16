package org.radarbase.push.integration.common.redis.offset

import java.util.*

class UserRoute(val userId: String, val route: String) : Comparable<UserRoute> {
    private val hash = Objects.hash(userId, route)

    override fun hashCode(): Int = hash

    override fun compareTo(other: UserRoute): Int = compareValuesBy(
        this, other,
        UserRoute::userId, UserRoute::route
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserRoute

        if (userId != other.userId) return false
        if (route != other.route) return false

        return true
    }
}
