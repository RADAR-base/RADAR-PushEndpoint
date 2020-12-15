package org.radarbase.push.integration.garmin.backfill

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminActivitiesRoute(
    private val consumerKey: String,
    private val consumerSecret: String,
    private val userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "activities"

    override fun toString(): String = "garmin_activities"
}
