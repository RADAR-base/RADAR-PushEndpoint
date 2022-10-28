package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository
import java.time.Duration

class GarminMoveIQRoute(
    consumerKey: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, userRepository) {

    override fun subPath(): String = "moveiq"

    override fun toString(): String = "garmin_move_iq"

    override fun maxBackfillPeriod(): Duration {
        // 2 years default. Activity API  routes will override this with 5 years
        return Duration.ofDays(365 * 2)
    }
}
