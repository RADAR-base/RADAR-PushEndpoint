package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminBloodPressureRoute(
    consumerKey: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, userRepository) {

    override fun subPath(): String = "bloodPressures"

    override fun toString(): String = "garmin_blood_pressures"
}
