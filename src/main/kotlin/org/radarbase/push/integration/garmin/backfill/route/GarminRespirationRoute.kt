package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminRespirationRoute(
    consumerKey: String,
    consumerSecret: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "respiration"

    override fun toString(): String = "garmin_respiration"
}
