package org.radarbase.push.integration.garmin.backfill

import okhttp3.Response
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.radarbase.push.integration.garmin.util.offset.*
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class GarminRequestGenerator(
    val config: Config,
    private val userRepository: GarminUserRepository,
    private val redisHolder: RedisHolder =
        RedisHolder(JedisPool(config.pushIntegration.garmin.backfill.redis.uri)),
    private val offsetPersistenceFactory: OffsetPersistenceFactory =
        OffsetRedisPersistence(redisHolder),
    private val defaultQueryRange: Duration = Duration.ofDays(5),
) :
    RequestGenerator {

    private val routes: List<Route> = listOf(
        GarminActivitiesRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
    )

    override fun requests(user: User, max: Int): List<RestRequest> {
        return routes.map { route ->
            val offsets: Offsets? = offsetPersistenceFactory.read(user.id)
            val startDate = userRepository.getBackfillStartDate(user)
            val offset: Instant = if (offsets == null) {
                // no offsets present for user, take the start date
                logger.debug("No offsets found for $user, using the start date.")
                startDate
            } else {
                logger.debug("Offsets found in persistence.")
                offsets.offsetsMap.getOrDefault(
                    UserRoute(user.id, route.toString()), startDate
                ).takeIf { it >= startDate } ?: startDate
            }
            val endDate = userRepository.getBackfillEndDate(user)
            if (endDate < offset.plus(defaultQueryRange)) {
                emptyList()
            } else {
                route.generateRequests(
                    user,
                    offset,
                    offset.plus(defaultQueryRange),
                    max / routes.size
                )
            }
        }.flatten()
    }

    override fun requestSuccessful(request: RestRequest, response: Response) {
        val offsets: Offsets? = offsetPersistenceFactory.read(request.user.id)
        offsetPersistenceFactory.writer(Path.of(request.user.id), offsets).use {
            it.add(UserRouteOffset(request.user.id, request.route.toString(), request.endDate))
        }
    }

    override fun requestFailed(request: RestRequest, response: Response) {
        logger.warn("Request Failed: {}, {}", request, response)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminRequestGenerator::class.java)
    }
}
