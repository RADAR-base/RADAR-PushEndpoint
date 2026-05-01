/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.inject.Named
import jakarta.ws.rs.core.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GOOGLE_HEALTH_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.google.converter.DailyRestingHeartRateGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.DailySleepTemperatureDerivationsGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.ExerciseGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.GoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.HeartRateGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.HeartRateVariabilityGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.OxygenSaturationGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.RespiratoryRateSleepSummaryGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.SleepClassicGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.SleepStageGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.StepsGoogleHealthAvroConverter
import org.radarbase.push.integration.google.converter.TotalCaloriesGoogleHealthAvroConverter
import org.radarbase.push.integration.garmin.util.offset.OffsetRedisPersistence
import org.radarbase.push.integration.garmin.util.offset.UserRoute
import org.radarbase.push.integration.garmin.util.offset.UserRouteOffset
import org.radarbase.push.integration.google.exceptions.TransientGoogleHealthException
import org.radarbase.push.integration.google.model.GoogleHealthPing
import org.radarbase.push.integration.google.model.PingInterval
import org.radarbase.push.integration.google.user.GoogleHealthUserRepository
import org.radarbase.push.integration.google.util.GoogleHealthPingDedup
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class GoogleHealthApiService(
    @param:Named(GOOGLE_HEALTH_QUALIFIER) @param:Context private val userRepository: GoogleHealthUserRepository,
    @param:Context private val producerPool: ProducerPool,
    @param:Context private val httpClient: OkHttpClient,
    @param:Context private val config: Config,
    @param:Context private val offsets: OffsetRedisPersistence,
) {
    private val googleConfig = config.pushIntegration.googlehealth
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(2),
    )
    private val dedup = GoogleHealthPingDedup(ttl = DEDUP_TTL)
    private val userSemaphores = ConcurrentHashMap<String, Semaphore>()
    private val apiBaseUrl: HttpUrl = normalizedBaseUrl(googleConfig.apiBaseUrl)

    private val converters: Map<String, List<GoogleHealthAvroConverter>> = buildConverters()

    private val nonSubscribedTypes: List<String> =
        googleConfig.enabledDataTypes.filter { it !in googleConfig.triggerDataTypes }

    fun handlePing(ping: GoogleHealthPing) {
        if (ping.intervals.isEmpty()) {
            logger.info("Ignoring PING with no intervals for user {}", ping.healthUserId)
            return
        }
        val freshIntervals = ping.intervals.filter { interval ->
            val dedupKey = dedupKeyFor(ping.healthUserId, interval)
            dedup.claim(dedupKey).also { claimed ->
                if (!claimed) logger.info("Skipping duplicate PING interval {}", dedupKey)
            }
        }
        if (freshIntervals.isEmpty()) return
        executor.submit {
            try {
                fetchAllForUser(ping, freshIntervals)
            } catch (ex: Exception) {
                logger.error("Unhandled error while processing PING for user {}", ping.healthUserId, ex)
            }
        }
    }

    private fun fetchAllForUser(ping: GoogleHealthPing, intervals: List<PingInterval>) {
        val user = resolveUser(ping.healthUserId) ?: return
        if (!user.isAuthorized) {
            logger.info("Skipping PING for unauthorized user {}", user.id)
            return
        }

        if (ping.dataType in googleConfig.enabledDataTypes) {
            for (interval in intervals) {
                val window = widenInterval(interval)
                try {
                    fetchAndPublishBlocking(user, ping.dataType, window)
                } catch (ex: Exception) {
                    logger.error(
                        "Failed to fetch {} for user {} interval=[{},{})",
                        ping.dataType,
                        user.userId,
                        interval.physicalStartTime,
                        interval.physicalEndTime,
                        ex,
                    )
                }
            }
        }

        if (nonSubscribedTypes.isEmpty()) return
        val target = intervals.maxOf { widenInterval(it).second }
        for (dataType in nonSubscribedTypes) {
            try {
                catchUpFetch(user, dataType, target)
            } catch (ex: Exception) {
                logger.error(
                    "Catch-up fetch failed for {} user={} target={}",
                    dataType, user.versionedId, target, ex,
                )
            }
        }
    }

    /**
     * Advance a non-subscribed type's stored offset forward to [target], one day per iteration.
     * Advances the Redis offset after each chunk returns a 200 (including empty responses —
     * `:reconcile` returning zero records means the user legitimately had no data in that window,
     * not that data is pending). Stops on [TransientGoogleHealthException] without advancing so
     * the next PING retries the same chunk.
     */
    private fun catchUpFetch(user: User, dataType: String, target: Instant) {
        val path = Path.of(user.versionedId)
        val route = liveRouteFor(dataType)
        val cutoff = ensureHistoricalCutoff(user)
        val storedOffset = offsets.read(user.versionedId)
            ?.offsetsMap?.get(UserRoute(user.versionedId, route))
        // Live cursor never reaches into the historical range owned by backfill — if there's no
        // stored live offset yet (new user, or first PING on this type), start exactly at cutoff.
        var cursor = storedOffset?.coerceAtLeast(cutoff) ?: cutoff
        if (!cursor.isBefore(target)) return
        while (cursor.isBefore(target)) {
            val chunkEnd = minOf(cursor.plus(CATCHUP_CHUNK), target)
            try {
                fetchAndPublishBlocking(user, dataType, cursor to chunkEnd)
            } catch (ex: TransientGoogleHealthException) {
                logger.warn(
                    "Catch-up transient failure user={} type={} chunk=[{},{}). Next PING will retry.",
                    user.versionedId, dataType, cursor, chunkEnd, ex,
                )
                return
            }
            offsets.add(path, UserRouteOffset(user.versionedId, route, chunkEnd))
            cursor = chunkEnd
        }
    }

    /**
     * Read or establish the user's historical-cutoff timestamp. This is the boundary between the
     * backfill service (owns `[user.startDate, cutoff]`) and the PING path (owns `[cutoff, now]`).
     * Captured lazily on first access by whichever path runs first.
     */
    fun ensureHistoricalCutoff(user: User): Instant {
        val path = Path.of(user.versionedId)
        return offsets.read(user.versionedId)
            ?.offsetsMap
            ?.get(UserRoute(user.versionedId, CUTOFF_ROUTE))
            ?: Instant.now()
                .minus(CUTOFF_LAG)
                .also { cutoff ->
                    offsets.add(path, UserRouteOffset(user.versionedId, CUTOFF_ROUTE, cutoff))
                }
    }

    private fun dedupKeyFor(healthUserId: String, interval: PingInterval): String =
        "$healthUserId:${interval.physicalStartTime}:${interval.physicalEndTime}"

    private fun liveRouteFor(dataType: String): String = "$LIVE_ROUTE_PREFIX$dataType"

    /**
     * Fetch and publish data for a single user, data type, and time window, blocking until all
     * pages are exhausted.
     * Per-user concurrency is bounded by [MAX_CONCURRENT_PER_USER] so simultaneous PING and backfill
     * traffic for the same user cannot exceed it.
     */
    fun fetchAndPublishBlocking(user: User, dataType: String, window: Pair<Instant, Instant>) {
        val perType = converters[dataType]
        if (perType.isNullOrEmpty()) {
            logger.debug("No converter registered for dataType={}", dataType)
            return
        }
        val sem = userSemaphores.computeIfAbsent(user.id) { Semaphore(MAX_CONCURRENT_PER_USER) }
        sem.acquire()
        try {
            var pageToken: String? = null
            do {
                val response = fetchDataPoints(user, dataType, window, pageToken)
                perType.forEach { conv ->
                    val records = conv.convert(response, user)
                    if (records.isNotEmpty()) producerPool.produce(conv.topic, records)
                }
                pageToken = response["nextPageToken"]?.asText()?.takeIf { it.isNotEmpty() }
            } while (pageToken != null)
        } finally {
            sem.release()
        }
    }

    private fun fetchDataPoints(
        user: User,
        dataType: String,
        window: Pair<Instant, Instant>,
        pageToken: String?,
    ): JsonNode {
        return when (dataType) {
            "total-calories" -> rollUpDataPoints(user, dataType, window, pageToken)
            else -> reconcileDataPoints(user, dataType, window, pageToken)
        }
    }

    private fun reconcileDataPoints(
        user: User,
        dataType: String,
        window: Pair<Instant, Instant>,
        pageToken: String?,
    ): JsonNode {
        val filter = buildFilterExpression(dataType, window)
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegments("users/me/dataTypes/$dataType/dataPoints:reconcile")
            .addQueryParameter("dataSourceFamily", "users/me/dataSourceFamilies/google-wearables")
            .addQueryParameter("filter", filter).addQueryParameter("pageSize", PAGE_SIZE.toString())
        if (!pageToken.isNullOrEmpty()) urlBuilder.addQueryParameter("pageToken", pageToken)

        val requestBuilder = { token: String ->
            Request.Builder().url(urlBuilder.build()).header("Authorization", "Bearer $token")
                .header("Accept", "application/json").get().build()
        }
        return executeWithRetry(user, requestBuilder)
    }

    private fun rollUpDataPoints(
        user: User,
        dataType: String,
        window: Pair<Instant, Instant>,
        pageToken: String?,
    ): JsonNode {
        val url = apiBaseUrl.newBuilder().addPathSegments("users/me/dataTypes/$dataType/dataPoints:rollUp").build()
        /**
         * NOTE: For `chunkSizeDays` + `total-calories`: Google's rollUp endpoint enforces
         * pageSize >= ceil(range_seconds / windowSize_seconds). With windowSize=60s a
         * 7-day chunk requires 10080 buckets.
         */
        val rangeSeconds = window.second.epochSecond - window.first.epochSecond
        val minBuckets = ((rangeSeconds + 59) / 60).toInt()  // ceil(range / 60s)
        val effectivePageSize = maxOf(PAGE_SIZE, minBuckets)
        val bodyNode = objectMapper.createObjectNode().apply {
            putObject("range").apply {
                put("startTime", ISO_FMT.format(window.first))
                put("endTime", ISO_FMT.format(window.second))
            }
            put("windowSize", "60s")
            put("pageSize", effectivePageSize)
            if (!pageToken.isNullOrEmpty()) put("pageToken", pageToken)
        }
        val body = objectMapper.writeValueAsString(bodyNode).toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = { token: String ->
            Request.Builder().url(url).header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json").post(body).build()
        }
        return executeWithRetry(user, requestBuilder)
    }

    private fun executeWithRetry(user: User, buildRequest: (String) -> Request): JsonNode {
        var token = userRepository.getOAuth2AccessToken(user)
        var tokenRefreshed = false
        var rateLimitAttempts = 0
        var rateLimitBackoff = RATE_LIMIT_INITIAL_BACKOFF
        var serverErrorAttempts = 0
        var serverErrorBackoff = SERVER_ERROR_INITIAL_BACKOFF
        while (true) {
            val request = buildRequest(token)
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                when {
                    resp.isSuccessful -> return parseBody(resp.body?.string())
                    resp.code == 401 && !tokenRefreshed -> {
                        logger.info("401 from Google Health — refreshing token for {}", user.id)
                        token = userRepository.getOAuth2AccessToken(user)
                        tokenRefreshed = true
                    }

                    resp.code == 403 -> {
                        logger.info("403 from Google Health — skipping request for user={}", user.id)
                        return emptyResponse()
                    }

                    resp.code == 404 -> return emptyResponse()
                    resp.code == 400 -> {
                        // Surface filter-shape mismatches loudly — silent empty responses
                        // hide bugs like "Member 'X' is not supported for filtering".
                        logger.error(
                            "400 from Google Health for user={} url={} body={}",
                            user.id,
                            request.url,
                            resp.peekBody(MAX_ERROR_BODY_BYTES).string(),
                        )
                        return emptyResponse()
                    }

                    resp.code == 429 -> {
                        // Throw on exhaustion so the backfill loop does NOT advance its cursor past a
                        // chunk that never completed. The next scheduled iteration will re-fetch.
                        if (rateLimitAttempts >= MAX_RATE_LIMIT_RETRIES) {
                            throw TransientGoogleHealthException(
                                "429 retries exhausted for user=${user.id}",
                            )
                        }
                        sleepWithJitter(rateLimitBackoff)
                        rateLimitBackoff = rateLimitBackoff.multipliedBy(2)
                            .let { if (it > RATE_LIMIT_MAX_BACKOFF) RATE_LIMIT_MAX_BACKOFF else it }
                        rateLimitAttempts++
                    }

                    resp.code in 500..599 -> {
                        if (serverErrorAttempts >= MAX_5XX_RETRIES) {
                            throw TransientGoogleHealthException(
                                "5xx retries exhausted for user=${user.id} code=${resp.code}",
                            )
                        }
                        sleepWithJitter(serverErrorBackoff)
                        serverErrorBackoff = serverErrorBackoff.multipliedBy(2)
                            .let { if (it > SERVER_ERROR_MAX_BACKOFF) SERVER_ERROR_MAX_BACKOFF else it }
                        serverErrorAttempts++
                    }

                    else -> {
                        logger.warn(
                            "Unexpected {} from Google Health for user={}",
                            resp.code,
                            user.id,
                        )
                        return emptyResponse()
                    }
                }
            }
        }
    }

    private fun buildFilterExpression(dataType: String, window: Pair<Instant, Instant>): String {
        val stem = dataType.replace('-', '_')
        val startText = ISO_FMT.format(window.first)
        val endText = ISO_FMT.format(window.second)

        // Reference: https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/list
        return when (timeAxisFor(dataType)) {
            TimeAxis.INTERVAL -> "$stem.interval.start_time >= \"$startText\" AND $stem.interval.start_time < \"$endText\""
            TimeAxis.SLEEP_INTERVAL -> "$stem.interval.end_time >= \"$startText\" AND $stem.interval.end_time < \"$endText\""
            TimeAxis.SAMPLE -> "$stem.sample_time.physical_time >= \"$startText\" AND $stem.sample_time.physical_time < \"$endText\""

            TimeAxis.CIVIL_INTERVAL -> {
                val civilStart = CIVIL_DT_FMT.format(window.first)
                val civilEnd = CIVIL_DT_FMT.format(window.second)
                "$stem.interval.civil_start_time >= \"$civilStart\" AND $stem.interval.civil_start_time < \"$civilEnd\""
            }

            TimeAxis.DAILY -> {
                val startDate = window.first.atZone(java.time.ZoneOffset.UTC).toLocalDate()

                /**
                 * Google supports `>=` and `<` only for daily date filters. Advance endDate by one day so a same-day window still matches.
                 * A PING interval that lives within 2026-04-17 becomes `date >= "2026-04-17" AND date < "2026-04-18"`.
                 */
                val endDate = window.second.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1)
                "$stem.date >= \"$startDate\" AND $stem.date < \"$endDate\""
            }
        }
    }

    private fun timeAxisFor(dataType: String): TimeAxis = when (dataType) {
        "steps", "altitude", "distance", "floors", "total-calories" -> TimeAxis.INTERVAL
        "exercise" -> TimeAxis.CIVIL_INTERVAL
        "sleep" -> TimeAxis.SLEEP_INTERVAL
        "heart-rate", "heart-rate-variability", "oxygen-saturation", "respiratory-rate-sleep-summary", "weight", "body-fat" -> TimeAxis.SAMPLE
        "daily-resting-heart-rate", "daily-sleep-temperature-derivations" -> TimeAxis.DAILY
        else -> TimeAxis.INTERVAL
    }

    private fun resolveUser(healthUserId: String): User? {
        return try {
            userRepository.findByExternalId(healthUserId)
        } catch (_: NoSuchElementException) {
            try {
                userRepository.applyPendingUpdates()
                userRepository.findByExternalId(healthUserId)
            } catch (_: NoSuchElementException) {
                logger.info("No GoogleHealth user matches healthUserId={}", healthUserId)
                null
            }
        }
    }

    private fun parseBody(body: String?): JsonNode {
        if (body.isNullOrEmpty()) return emptyResponse()
        return objectMapper.readTree(body)
    }

    private fun emptyResponse(): JsonNode = objectMapper.createObjectNode()

    private fun widenInterval(interval: PingInterval): Pair<Instant, Instant> {
        return interval.physicalStartTime.minus(OVERLAP) to interval.physicalEndTime.plus(OVERLAP)
    }

    private fun buildConverters(): Map<String, List<GoogleHealthAvroConverter>> = mapOf(
        "steps" to listOf(StepsGoogleHealthAvroConverter(googleConfig.stepsTopicName)),
        "heart-rate" to listOf(HeartRateGoogleHealthAvroConverter(googleConfig.heartRateTopicName)),
        "heart-rate-variability" to listOf(
            HeartRateVariabilityGoogleHealthAvroConverter(googleConfig.heartRateVariabilityTopicName),
        ),
        "oxygen-saturation" to listOf(
            OxygenSaturationGoogleHealthAvroConverter(googleConfig.oxygenSaturationTopicName),
        ),
        "total-calories" to listOf(
            TotalCaloriesGoogleHealthAvroConverter(googleConfig.totalCaloriesTopicName),
        ),
        "daily-resting-heart-rate" to listOf(
            DailyRestingHeartRateGoogleHealthAvroConverter(googleConfig.dailyRestingHeartRateTopicName),
        ),
        "respiratory-rate-sleep-summary" to listOf(
            RespiratoryRateSleepSummaryGoogleHealthAvroConverter(
                googleConfig.respiratoryRateSleepSummaryTopicName,
            ),
        ),
        "daily-sleep-temperature-derivations" to listOf(
            DailySleepTemperatureDerivationsGoogleHealthAvroConverter(
                googleConfig.dailySleepTemperatureDerivationsTopicName,
            ),
        ),
        "sleep" to listOf(
            SleepStageGoogleHealthAvroConverter(googleConfig.sleepStagesTopicName),
            SleepClassicGoogleHealthAvroConverter(googleConfig.sleepClassicTopicName),
        ),
        "exercise" to listOf(
            ExerciseGoogleHealthAvroConverter(googleConfig.exerciseTopicName),
        ),
    )

    private fun sleepWithJitter(base: Duration) {
        val jitterMs = (Math.random() * 1000).toLong()
        Thread.sleep(base.toMillis() + jitterMs)
    }

    private fun normalizedBaseUrl(raw: String) = if (raw.endsWith("/")) raw.toHttpUrl() else "$raw/".toHttpUrl()

    companion object {
        private enum class TimeAxis { INTERVAL, CIVIL_INTERVAL, SLEEP_INTERVAL, SAMPLE, DAILY }

        private val logger = LoggerFactory.getLogger(GoogleHealthApiService::class.java)

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val ISO_FMT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        private val CIVIL_DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(java.time.ZoneOffset.UTC)

        private const val PAGE_SIZE = 1000
        private const val MAX_CONCURRENT_PER_USER = 3
        private const val MAX_RATE_LIMIT_RETRIES = 3
        private const val MAX_5XX_RETRIES = 5
        private const val MAX_ERROR_BODY_BYTES = 4096L

        const val LIVE_ROUTE_PREFIX = "gh:live:"
        const val BACKFILL_ROUTE_PREFIX = "gh:bf:"
        const val CUTOFF_ROUTE = "gh:_historical_cutoff"

        private val DEDUP_TTL: Duration = Duration.ofMinutes(5)
        private val OVERLAP: Duration = Duration.ofMinutes(2)
        private val CATCHUP_CHUNK: Duration = Duration.ofDays(1)
        private val CUTOFF_LAG: Duration = Duration.ofHours(1)
        private val RATE_LIMIT_INITIAL_BACKOFF: Duration = Duration.ofSeconds(30)
        private val RATE_LIMIT_MAX_BACKOFF: Duration = Duration.ofMinutes(10)
        private val SERVER_ERROR_INITIAL_BACKOFF: Duration = Duration.ofSeconds(2)
        private val SERVER_ERROR_MAX_BACKOFF: Duration = Duration.ofSeconds(60)
    }
}



