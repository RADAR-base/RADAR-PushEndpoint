package org.radarbase.push.integration.garmin.backfill

import okhttp3.Response
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.backfill.route.*
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
    private val defaultQueryRange: Duration = Duration.ofDays(15),
) :
    RequestGenerator {

    private val routes: List<Route> = listOf(
        GarminActivitiesRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminDailiesRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminActivityDetailsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminBodyCompsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminEpochsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminMoveIQRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminPulseOxRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminRespirationRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminSleepsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminStressDetailsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        ),
        GarminUserMetricsRoute(
            config.pushIntegration.garmin.consumerKey,
            config.pushIntegration.garmin.consumerSecret,
            userRepository
        )
    )

    override fun requests(user: User, max: Int): List<RestRequest> {
        return routes.map { route ->
            val offsets: Offsets? = offsetPersistenceFactory.read(user.versionedId)
            val startDate = userRepository.getBackfillStartDate(user)
            val startOffset: Instant = if (offsets == null) {
                logger.debug("No offsets found for $user, using the start date.")
                startDate
            } else {
                logger.debug("Offsets found in persistence.")
                offsets.offsetsMap.getOrDefault(
                    UserRoute(user.versionedId, route.toString()), startDate
                ).takeIf { it >= startDate } ?: startDate
            }
            val endDate = userRepository.getBackfillEndDate(user)
            val endTime = when {
                endDate <= startOffset -> return@map emptyList() // Already at end. No further requests
                endDate < startOffset.plus(defaultQueryRange) -> endDate
                else -> startOffset.plus(defaultQueryRange)
            }
            route.generateRequests(user, startOffset, endTime, max / routes.size)
        }.flatten()
    }

    override fun requestSuccessful(request: RestRequest, response: Response) {
        logger.debug("Request successful: {}. Writing to offsets...", request.request)
        val offsets: Offsets? = offsetPersistenceFactory.read(request.user.versionedId)
        offsetPersistenceFactory.writer(Path.of(request.user.versionedId), offsets).use {
            it.add(
                UserRouteOffset(
                    request.user.versionedId,
                    request.route.toString(),
                    request.endDate
                )
            )
        }
    }

    override fun requestFailed(request: RestRequest, response: Response) {
        logger.warn("Request Failed: {}, {}", request, response)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminRequestGenerator::class.java)
    }
}
