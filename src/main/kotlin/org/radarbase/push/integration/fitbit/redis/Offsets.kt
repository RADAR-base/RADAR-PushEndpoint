package org.radarbase.push.integration.fitbit.redis

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class Offsets(val offsetsMap: ConcurrentMap<UserRoute, FitbitOffsets> = ConcurrentHashMap()) {
    fun add(userRouteOffset: UserRouteOffset) {
        offsetsMap[userRouteOffset.userRoute] = FitbitOffsets(userRouteOffset.lastSuccessOffset, userRouteOffset.latestOffset)
    }

    fun addAll(offsets: Offsets) {
        offsetsMap.putAll(offsets.offsetsMap)
    }
}

data class FitbitOffsets(
    var lastSuccessOffset: Instant,
    var latestOffset: Instant
)
