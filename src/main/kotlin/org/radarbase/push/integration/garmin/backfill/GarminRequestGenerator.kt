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
            userRepository
        ),
        GarminDailiesRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminActivityDetailsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminBodyCompsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminEpochsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminMoveIQRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminPulseOxRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminRespirationRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminSleepsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminStressDetailsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        ),
        GarminUserMetricsRoute(
            config.pushIntegration.garmin.consumerKey,
            userRepository
        )
    )

    private var nextRequestTime: Instant = Instant.MIN

    private val shouldBackoff: Boolean
        get() = Instant.now() < nextRequestTime

    override fun requests(user: User, max: Int): Sequence<RestRequest> {
        return if (!shouldBackoff) {
            routes.map { route ->
                val offsets: Offsets? = offsetPersistenceFactory.read(user.versionedId)
                val startDate = userRepository.getBackfillStartDate(user)
                val startOffset: Instant = if (offsets == null) {
                    logger.debug("No offsets found for $user, using the start date.")
                    startDate
                } else {
                    logger.debug("Offsets found in persistence.")
                    offsets.offsetsMap.getOrDefault(
                        UserRoute(user.versionedId, route.toString()), startDate
                    ).coerceAtLeast(startDate)
                }
                val endDate = userRepository.getBackfillEndDate(user)
                if (endDate <= startOffset) return@map emptyList()
                val endTime = (startOffset + defaultQueryRange).coerceAtMost(endDate)
                route.generateRequests(user, startOffset, endTime, max / routes.size)
            }.asSequence().flatten().takeWhile { !shouldBackoff }
        } else emptySequence()
    }

    override fun requestSuccessful(request: RestRequest, response: Response) {
        logger.debug("Request successful: {}. Writing to offsets...", request.request)
        offsetPersistenceFactory.add(
            Path.of(request.user.versionedId), UserRouteOffset(
                request.user.versionedId,
                request.route.toString(),
                request.endDate
            )
        )
    }

    override fun requestFailed(request: RestRequest, response: Response) {
        when (response.code) {
            429 -> {
                logger.info("Too many requests, rate limit reached. Backing off...")
                nextRequestTime = Instant.now().plusMillis(BACK_OFF_TIME_MS)
            }
            409 -> {
                logger.info("A duplicate request was made. Marking successful...")
                requestSuccessful(request, response)
            }
            else -> logger.warn("Request Failed: {}, {}", request, response)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminRequestGenerator::class.java)
        private const val BACK_OFF_TIME_MS = 60_000L
    }
}
