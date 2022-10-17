package org.radarbase.push.integration.garmin.util.offset

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

class UserRouteOffset(val userRoute: UserRoute, val offset: Instant) {
    @JsonIgnore
    val userId: String = userRoute.userId

    @JsonIgnore
    val route: String = userRoute.route

    constructor(userId: String, route: String, offset: Instant): this(
        UserRoute(userId, route),
        offset
    )

    override fun toString(): String {
        return "$userId+$route ($offset)"
    }


}
