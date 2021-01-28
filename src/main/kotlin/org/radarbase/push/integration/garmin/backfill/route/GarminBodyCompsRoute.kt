package org.radarbase.push.integration.garmin.backfill.route

import org.radarbase.push.integration.garmin.user.GarminUserRepository

class GarminBodyCompsRoute(
    consumerKey: String,
    consumerSecret: String,
    userRepository: GarminUserRepository
) : GarminRoute(consumerKey, consumerSecret, userRepository) {

    override fun subPath(): String = "bodyComps"

    override fun toString(): String = "garmin_body_comps"
}
