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
) : RequestGenerator {

    private val routes: List<Route> =
        mutableListOf<Route>().apply {
            if (config.pushIntegration.garmin.backfill.activitiesEnabled) {
                add(GarminActivitiesRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.dailiesEnabled) {
                add(GarminDailiesRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.activityDetailsEnabled) {
                add(GarminActivityDetailsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.bodyCompositionsEnabled) {
                add(GarminBodyCompsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.epochSummariesEnabled) {
                add(GarminEpochsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.moveIQEnabled) {
                add(GarminMoveIQRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.pulseOXEnabled) {
                add(GarminPulseOxRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.respirationEnabled) {
                add(GarminRespirationRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.sleepsEnabled) {
                add(GarminSleepsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.stressEnabled) {
                add(GarminStressDetailsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.userMetricsEnabled) {
                add(GarminUserMetricsRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.healthSnapshotEnabled) {
                add(GarminHealthSnapshotRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.heartRateVariabilityEnabled) {
                add(GarminHrvRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
            if (config.pushIntegration.garmin.backfill.bloodPressureEnabled) {
                add(GarminBloodPressureRoute(
                    config.pushIntegration.garmin.consumerKey,
                    userRepository
                ))
            }
        }.toList()

    private val userNextRequest: MutableMap<String, Instant> = mutableMapOf()

    private var nextRequestTime: Instant = Instant.MIN

    private val shouldBackoff: Boolean
        get() = Instant.now() < nextRequestTime

    override fun requests(user: User, max: Int): Sequence<RestRequest> {
        return if (user.ready()) {
            routes.asSequence()
                .flatMap { route ->
                    val offsets: Offsets? = offsetPersistenceFactory.read(user.versionedId)
                    val backfillLimit = Instant.now().minus(route.maxBackfillPeriod())
                    val startDate = userRepository.getBackfillStartDate(user)
                    var startOffset: Instant = if (offsets == null) {
                        logger.debug("No offsets found for $user, using the start date.")
                        startDate
                    } else {
                        logger.debug("Offsets found in persistence.")
                        offsets.offsetsMap.getOrDefault(
                            UserRoute(user.versionedId, route.toString()), startDate
                        ).coerceAtLeast(startDate)
                    }

                    if (startOffset <= backfillLimit) {
                        // the start date is before the backfill limits
                        logger.warn(
                            "Backfill limit exceeded for $user and $route. " +
                                "Resetting to earliest allowed start offset."
                        )
                        startOffset = backfillLimit.plus(Duration.ofDays(2))
                    }

                    val endDate = userRepository.getBackfillEndDate(user)
                    if (endDate <= startOffset) return@flatMap emptySequence()
                    val endTime = (startOffset + defaultQueryRange).coerceAtMost(endDate)
                    route.generateRequests(user, startOffset, endTime, max / routes.size)
                }
                .takeWhile { !shouldBackoff }
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
                nextRequestTime = Instant.now() + BACK_OFF_TIME
                throw TooManyRequestsException()
            }
            409 -> {
                logger.info("A duplicate request was made. Marking successful...")
                requestSuccessful(request, response)
            }
            412 -> {
                logger.warn(
                    "User ${request.user} does not have correct permissions/scopes enabled. " +
                        "Please enable in garmin connect. User backing off for $USER_BACK_OFF_TIME..."
                )
                userNextRequest[request.user.versionedId] = Instant.now().plus(USER_BACK_OFF_TIME)
            }
            else -> logger.warn("Request Failed: {}, {}", request, response)
        }
    }

    private fun User.ready(): Boolean {
        return if (versionedId in userNextRequest) {
            Instant.now() > userNextRequest[versionedId]
        } else {
            true
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminRequestGenerator::class.java)
        private val BACK_OFF_TIME = Duration.ofMinutes(1L)
        private val USER_BACK_OFF_TIME = Duration.ofDays(1L)
    }
}
