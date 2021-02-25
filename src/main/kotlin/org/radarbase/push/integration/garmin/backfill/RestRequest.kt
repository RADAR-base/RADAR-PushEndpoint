package org.radarbase.push.integration.garmin.backfill

import okhttp3.Request
import org.radarbase.push.integration.common.user.User
import java.time.Instant

data class RestRequest(val request: Request,
                       val user: User,
                       val route: Route,
                       val startDate: Instant,
                       val endDate: Instant)
