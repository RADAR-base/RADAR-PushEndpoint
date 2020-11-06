package org.radarbase.push.integration.garmin.service

import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import javax.inject.Named
import javax.ws.rs.core.Context

/**
 * The backfill service should be used to collect historic data. This will send requests to garmin's
 * service to create POST requests for historic data to our server.
 */
class BackfillService(
    @Context config: Config,
    @Named(GARMIN_QUALIFIER) userRepository: GarminUserRepository
) {
    // TODO: discuss about this since we need some way to store the offset for historic requests
    //  made for each user
}
