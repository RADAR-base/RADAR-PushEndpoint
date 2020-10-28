package org.radarbase.push.integration.common.user

import org.radarcns.kafka.ObservationKey
import java.time.Instant

interface User {
    val id: String
    val projectId: String
    val userId: String
    val sourceId: String
    val externalUserId: String
    val startDate: Instant
    val endDate: Instant
    val version: String?
    val isAuthorized: Boolean
    val versionedId: String
        get() {
            val version = version
            return if (version == null) {
                id
            } else {
                "$id#$version"
            }
        }
    val observationKey: ObservationKey
/*    val isComplete: Boolean?
        get() = endDate != null
                && startDate != null
                && projectId != null
                && userId != null
                && isAuthorized*/
}
