package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminSleepsRoute(
    consumerKey: String,
    consumerSecret: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "sleeps"

    override fun toString(): String = "garmin_sleeps"
}
