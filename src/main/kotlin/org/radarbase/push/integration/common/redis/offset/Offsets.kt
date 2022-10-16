package org.radarbase.push.integration.common.redis.offset

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class Offsets(val offsetsMap: ConcurrentMap<UserRoute, Instant> = ConcurrentHashMap()) {
    fun add(userRouteOffset: UserRouteOffset) {
        offsetsMap[userRouteOffset.userRoute] = userRouteOffset.offset
    }

    fun addAll(offsets: Offsets) {
        offsetsMap.putAll(offsets.offsetsMap)
    }
}
