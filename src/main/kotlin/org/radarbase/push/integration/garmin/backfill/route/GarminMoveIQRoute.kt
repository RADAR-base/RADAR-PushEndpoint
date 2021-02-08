package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminMoveIQRoute(
    consumerKey: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, userRepository) {

    override fun subPath(): String = "moveiq"

    override fun toString(): String = "garmin_move_iq"
}
