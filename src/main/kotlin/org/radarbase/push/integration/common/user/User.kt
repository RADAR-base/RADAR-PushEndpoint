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
    val createdAt: Instant
    val version: String?
    val isAuthorized: Boolean
    val observationKey: ObservationKey
    val versionedId: String
}
