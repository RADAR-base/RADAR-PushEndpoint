package org.radarbase.push.integration.fitbit.redis

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

class UserRouteOffset(
    val userRoute: UserRoute,
    val lastSuccessOffset: Instant,
    val latestOffset: Instant
) {
    @JsonIgnore
    val userId: String = userRoute.userId

    @JsonIgnore
    val route: String = userRoute.route

    constructor(userId: String, route: String, lastSuccessOffset: Instant, latestOffset: Instant) : this(
        UserRoute(userId, route),
        lastSuccessOffset,
        latestOffset
    )

    override fun toString(): String {
        return "$userId+$route (lastSuccessOffset: $lastSuccessOffset, latestOffset: $latestOffset)"
    }


}
