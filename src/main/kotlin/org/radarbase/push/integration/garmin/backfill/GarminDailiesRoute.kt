package org.radarbase.push.integration.garmin.backfill

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminDailiesRoute(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "dailies"

    override fun toString(): String = "garmin_dailies"
}
