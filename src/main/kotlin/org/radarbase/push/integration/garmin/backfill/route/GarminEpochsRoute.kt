package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminEpochsRoute(
    consumerKey: String,
    consumerSecret: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "epochs"

    override fun toString(): String = "garmin_epochs"
}
