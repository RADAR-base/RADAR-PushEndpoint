package org.radarbase.push.integration.oura.offset

import okhttp3.Response
import org.radarbase.oura.route.Route
import org.radarbase.oura.user.User
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.time.Instant
import org.radarbase.oura.request.OuraOffsetManager
import org.radarbase.oura.offset.Offset
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.radarbase.push.integration.garmin.util.offset.*
import java.nio.file.Path
import java.net.URI

class OuraRedisOffsetManager(
    val redisUri: URI,
    private val redisHolder: RedisHolder =
        RedisHolder(JedisPool(redisUri)),
    private val offsetPersistenceFactory: OffsetPersistenceFactory =
        OffsetRedisPersistence(redisHolder),
): OuraOffsetManager {

    override fun getOffset(route: Route, user: User): Offset? {
        logger.info("Getting offset..")
        val offsets = offsetPersistenceFactory.read(user.versionedId)
        if (offsets == null) return null
        return Offset(user.userId, route.toString(), offsets.offsetsMap.getOrDefault(
                            UserRoute(user.versionedId, route.toString()), user.startDate
                        ).coerceAtLeast(user.startDate))
    }

    override fun updateOffsets(route: Route, user: User, offset: Instant) {
        logger.info("Writing to offsets..")
        offsetPersistenceFactory.add(
            Path.of(user.versionedId), UserRouteOffset(
                user.versionedId,
                route.toString(),
                offset
            )
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OuraRedisOffsetManager::class.java)
    }
}
