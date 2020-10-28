package org.radarbase.push.integrations.garmin.service

import org.radarbase.gateway.Config
import org.radarbase.push.integrations.common.user.UserRepository
import javax.ws.rs.core.Context

/**
 * The backfill service should be used to collect historic data. This will send requests to garmin's
 * service to create backfill POST requests to our server.
 */
class BackfillService(@Context config: Config, @Context userRepository: UserRepository) {

}
