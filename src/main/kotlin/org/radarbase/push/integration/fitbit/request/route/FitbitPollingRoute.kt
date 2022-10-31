/*
 * Copyright 2018 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.radarbase.push.integration.fitbit.request.route

import jakarta.ws.rs.NotAuthorizedException
import okhttp3.Request
import okhttp3.Response
import org.apache.avro.generic.IndexedRecord
import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.redis.RedisHolder
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.radarbase.push.integration.fitbit.converter.DateRange
import org.radarbase.push.integration.fitbit.converter.PayloadToSourceRecordConverter.Companion.MIN_INSTANT
import org.radarbase.push.integration.fitbit.converter.PayloadToSourceRecordConverter.Companion.nearFuture
import org.radarbase.push.integration.fitbit.converter.TopicData
import org.radarbase.push.integration.fitbit.redis.*
import org.radarbase.push.integration.fitbit.request.FitbitRequestGenerator
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import org.radarbase.push.integration.fitbit.request.route.PollingRequestRoute.Companion.max
import org.radarcns.kafka.ObservationKey
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Route for regular polling.
 *
 *
 * The algorithm uses the following polling times:
 * 1. do not try polling until getLastPoll() + getPollInterval()
 * 2. if that has passed, determine for each user when to poll again. Per user:
 * 1. if a successful call was made that returned data, take the last successful offset and after
 * getLookbackTime() has passed, poll again.
 * 2. if a successful call was made that did not return data, take the last query interval
 * and start cycling up from the last successful record, starting no further than
 * HISTORICAL_TIME
 *
 *
 * Conditions that should be met:
 * 1. Do not poll more frequently than once every getPollInterval().
 * 2. On first addition of a user, poll its entire history
 * 3. If the history of a user has been scanned, do not look back further than
 * `HISTORICAL_TIME`. This ensures fewer operations under normal operations, where Fitbit
 * data is fairly frequently updated.
 * 4. If there was data for a certain date time in an API, earlier date times are not polled. This
 * prevents duplicate data.
 * 5. From after the latest known date time, the history of the user is regularly inspected for new
 * records.
 * 6, All of the recent history is simultaneously inspected to prevent reading only later data in
 * a single batch that is added to the API.
 * 7. When a too many records exception occurs, do not poll for given user for
 * `TOO_MANY_REQUESTS_COOLDOWN`.
 */
abstract class FitbitPollingRoute(
    private val generator: FitbitRequestGenerator,
    private val userRepository: UserRepository,
    val routeName: String,
    private val config: Config,
    private val producerPool: ProducerPool,
    private val redisHolder: RedisHolder = RedisHolder(JedisPool(config.pushIntegration.garmin.backfill.redis.uri)),
    private val offsetPersistenceFactory: OffsetPersistenceFactory = OffsetRedisPersistence(redisHolder),
) : PollingRequestRoute {
    /** Committed offsets.  */
    private val lastPollPerUser: MutableMap<String, Instant> = HashMap()
    final override val pollInterval: Duration = config.pushIntegration.fitbit.routePollInterval
    final override var lastPoll: Instant = MIN_INSTANT
        private set
    private var baseUrl: String = config.pushIntegration.fitbit.baseUrl

    /**
     * Get the poll interval for a single user on a single route.
     */
    protected open var pollIntervalPerUser: Duration = config.pushIntegration.fitbit.pollIntervalPerUser
    private val tooManyRequestsForUser: MutableSet<User> = ConcurrentHashMap.newKeySet()
    private val tooManyRequestsCooldown: Duration? =
        config.pushIntegration.fitbit.tooManyRequestsCooldown.minus(pollIntervalPerUser)

    override fun requestSucceeded(request: FitbitRestRequest, record: Sequence<Result<TopicData>>) {
        lastPollPerUser[request.user.id] = lastPoll
        var maxOffset: Instant = Instant.MIN
        var topic: String? = null
        val recordList: List<Pair<ObservationKey, IndexedRecord>> = record.filter { it.isSuccess }.map {
            topic = it.getOrNull()?.topic ?: throw IOException("Topic was not specified")
            val data = Pair(request.user.observationKey, it.getOrNull()?.value ?: throw IOException("null data"))
            val currentOffset = request.getDateRange().end.toInstant()
            if (currentOffset > maxOffset) {
                maxOffset = currentOffset
            }
            data
        }.toList()
        if (recordList.isNotEmpty()) {
            producerPool.produce(topic ?: throw IOException("Topic was not specified"), recordList)
            addLastSuccessOffset(request.user, maxOffset)
        }
    }

    override fun requestEmpty(request: FitbitRestRequest) {
        lastPollPerUser[request.user.id] = lastPoll
        val endOffset: Instant = request.getDateRange().end.toInstant()
        if (ChronoUnit.DAYS.between(endOffset, lastPoll) >= HISTORICAL_TIME_DAYS) {
            addLastSuccessOffset(request.user, endOffset)
        }
    }

    override fun requestFailed(request: FitbitRestRequest, response: Response?) {
        if (response != null && response.code == 429) {
            val user: User = request.user
            tooManyRequestsForUser.add(user)
            val cooldownString = response.header("Retry-After")
            var cooldown = tooManyRequestsCooldown
            if (cooldownString != null) {
                cooldown = try {
                    Duration.ofSeconds(cooldownString.toLong())
                } catch (ex: NumberFormatException) {
                    tooManyRequestsCooldown
                }
            }
            val backOff = lastPoll.plus(cooldown)
            lastPollPerUser[user.id] = backOff
            logger.info(
                "Too many requests for user {}. Backing off until {}", user, backOff.plus(pollIntervalPerUser)
            )
        } else {
            logger.warn("Failed to make request {}", request)
        }
    }

    /**
     * Actually construct requests, based on the current offset
     * @param user Fitbit user
     * @return request to make
     */
    protected abstract fun createRequests(user: User): Sequence<FitbitRestRequest?>

    override fun requests(): Sequence<FitbitRestRequest> {
        tooManyRequestsForUser.clear()
        lastPoll = Instant.now()
        return try {
            userRepository.stream().map { u -> AbstractMap.SimpleImmutableEntry(u, nextPoll(u)) }
                .filter { u -> lastPoll.isAfter(u.value) }
                .sortedWith(java.util.Map.Entry.comparingByValue())
                .flatMap { u -> createRequests(u.key) }
                .filterNotNull()
        } catch (e: IOException) {
            logger.warn("Cannot read users")
            emptySequence()
        }
    }

    /** Get the time that this route should be polled again.  */
    override val timeOfNextRequest: Instant?
        get() = nextPolls().minWithOrNull(Comparator.naturalOrder()) ?: nearFuture()

    /**
     * Create a FitbitRestRequest for given arguments.
     * @param user Fitbit user
     * @param dateRange dates that may be queried in the request
     * @param urlFormatArgs format arguments to [.getUrlFormat].
     * @return request or `null` if the authorization cannot be arranged.
     */
    protected fun newRequest(
        user: User, dateRange: DateRange, vararg urlFormatArgs: Any?
    ): FitbitRestRequest? {
        val builder: Request.Builder = Request.Builder().url(String.format(getUrlFormat(baseUrl), *urlFormatArgs))
        return try {
            val request: Request =
                builder.header("Authorization", "Bearer " + userRepository.getAccessToken(user)).build()
            FitbitRestRequest(
                this, request, user, generator.getClient(user), dateRange
            ) { req -> !tooManyRequestsForUser.contains(req.user) }
        } catch (ex: NotAuthorizedException) {
            logger.warn(
                "User {} does not have a configured access token: {}. Skipping.", user, ex.toString()
            )
            null
        } catch (ex: IOException) {
            logger.warn(
                "User {} does not have a configured access token: {}. Skipping.", user, ex.toString()
            )
            null
        }
    }

    override fun nextPolls(): Sequence<Instant?> {
        return try {
            userRepository.stream().map { user: User -> nextPoll(user) }
        } catch (e: IOException) {
            logger.warn("Failed to read users for polling interval: {}", e.toString())
            sequenceOf(lastPoll.plus(pollInterval))
        }
    }

    protected fun getLatestOffset(user: User): Instant {
        return offsetPersistenceFactory.read(user.versionedId)?.offsetsMap?.get(
            UserRoute(
                user.versionedId,
                routeName
            )
        )?.latestOffset ?: user.startDate.minus(ONE_NANO)
    }

    protected fun getOffsets(user: User): FitbitOffsets? {
        return offsetPersistenceFactory.read(user.versionedId)?.offsetsMap?.get(
            UserRoute(
                user.versionedId,
                routeName
            )
        )
    }

    protected fun addLastSuccessOffset(user: User, lastSuccessOffset: Instant) {
        val offsets =
            offsetPersistenceFactory.read(user.versionedId)?.offsetsMap?.get(UserRoute(user.versionedId, routeName))

        offsetPersistenceFactory.add(
            path = Path.of(user.versionedId), offset = UserRouteOffset(
                userId = user.versionedId,
                route = routeName,
                lastSuccessOffset,
                offsets?.latestOffset ?: lastSuccessOffset
            )
        )
    }

    /**
     * URL String format. The format arguments should be provided to
     * [.newRequest]
     */
    protected abstract fun getUrlFormat(baseUrl: String?): String

    /**
     * Next time that given user should be polled.
     */
    protected fun nextPoll(user: User): Instant {
        val offset = getLatestOffset(user)
        return if (offset.isAfter(user.endDate.minus(endDateThreshold))) {
            nearFuture()
        } else {
            val nextPoll = lastPollPerUser.getOrDefault(user.id, MIN_INSTANT).plus(pollIntervalPerUser)
            max(offset.plus(lookbackTime), nextPoll)
        }
    }

    private val endDateThreshold: TemporalAmount
        get() = Duration.ofHours(1)

    companion object {
        protected val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        protected val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Time that should not be polled to avoid duplicate data.
         */
        val lookbackTime: Duration = Duration.ofDays(1) // 1 day
        const val HISTORICAL_TIME_DAYS = 14L
        val ONE_DAY: Duration = ChronoUnit.DAYS.duration
        val ONE_NANO: Duration = ChronoUnit.NANOS.duration
        val ONE_SECOND: TemporalAmount = ChronoUnit.SECONDS.duration
        val ONE_MINUTE: TemporalAmount = ChronoUnit.MINUTES.duration
        private val logger = LoggerFactory.getLogger(FitbitSleepRoute::class.java)
    }
}
