package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminHrvRoute(
    consumerKey: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, userRepository) {

    override fun subPath(): String = "hrv"

    override fun toString(): String = "garmin_hrv"
}
