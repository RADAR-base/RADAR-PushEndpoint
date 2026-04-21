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
import org.radarbase.push.integration.google.exceptions.TransientGoogleHealthException
import org.radarbase.push.integration.google.model.GoogleHealthPing
import org.radarbase.push.integration.google.model.PingInterval
import org.radarbase.push.integration.google.user.GoogleHealthUserRepository
import org.radarbase.push.integration.google.util.GoogleHealthPingDedup
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class GoogleHealthApiService(
    @param:Named(GOOGLE_HEALTH_QUALIFIER) @param:Context private val userRepository: GoogleHealthUserRepository,
    @param:Context private val producerPool: ProducerPool,
    @param:Context private val httpClient: OkHttpClient,
    @param:Context private val config: Config,
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

    fun handlePing(ping: GoogleHealthPing) {
        val firstInterval = ping.intervals.firstOrNull() ?: run {
            logger.info("Ignoring PING with no intervals for user {}", ping.healthUserId)
            return
        }
        val dedupKey = "${ping.healthUserId}:${firstInterval.physicalStartTime.truncatedTo(ChronoUnit.HOURS)}"
        if (!dedup.claim(dedupKey)) {
            logger.info("Skipping duplicate PING for {}", dedupKey)
            return
        }
        executor.submit {
            try {
                fetchAllForUser(ping, firstInterval)
            } catch (ex: Exception) {
                logger.error("Unhandled error while processing PING for user {}", ping.healthUserId, ex)
            }
        }
    }

    /**
     * Fetch and publish data for a single user, data type, and time window, blocking until all
     * pages are exhausted.
     * Per-user concurrency is bounded by [MAX_CONCURRENT_PER_USER] so simultaneous PING and backfill
     * traffic for the same user cannot exceed it.
     */
    private fun fetchAllForUser(ping: GoogleHealthPing, interval: PingInterval) {
        val user = resolveUser(ping.healthUserId) ?: return
        if (!user.isAuthorized) {
            logger.info("Skipping PING for unauthorized user {}", user.id)
            return
        }
        val window = widenInterval(interval, OVERLAP)
        for (dataType in googleConfig.enabledDataTypes) {
            try {
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
            } catch (ex: Exception) {
                logger.error("Failed to fetch {} for user {}", dataType, user.userId, ex)
            }
        }
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
        val urlBuilder = apiBaseUrl.newBuilder()
            .addPathSegments("users/me/dataTypes/$dataType/dataPoints:reconcile")
            .addQueryParameter("dataSourceFamily", "users/me/dataSourceFamilies/google-wearables")
            .addQueryParameter("filter", filter)
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
        if (!pageToken.isNullOrEmpty()) urlBuilder.addQueryParameter("pageToken", pageToken)

        val requestBuilder = { token: String ->
            Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()
        }
        return executeWithRetry(user, requestBuilder)
    }

    private fun rollUpDataPoints(
        user: User,
        dataType: String,
        window: Pair<Instant, Instant>,
        pageToken: String?,
    ): JsonNode {
        val url = apiBaseUrl.newBuilder()
            .addPathSegments("users/me/dataTypes/$dataType/dataPoints:rollUp")
            .build()
        // rollUp uses a JSON body, not query params. pageSize/pageToken go inside the body.
        // Reference: https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/rollUp
        //
        // Google enforces: pageSize >= ceil(range_seconds / window_size_seconds).
        // With windowSize=60s: must cover all 1-minute buckets in the range.
        // PAGE_SIZE=1000 safely covers PING windows (<2h = 120 buckets).
        // For backfill full-day windows, we compute the minimum explicitly so we never 400.
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
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
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

    private fun parseBody(body: String?): JsonNode {
        if (body.isNullOrEmpty()) return emptyResponse()
        return objectMapper.readTree(body)
    }

    private fun emptyResponse(): JsonNode = objectMapper.createObjectNode()

    private fun buildFilterExpression(
        dataType: String,
        window: Pair<Instant, Instant>,
    ): String {
        // Filter field paths per live docs:
        //   Interval:       {stem}.interval.start_time (UTC, RFC-3339 with Z) — steps/distance/floors/altitude/total-calories
        //   Civil interval: {stem}.interval.civil_start_time (local datetime, no Z) — exercise (session type, excluding sleep)
        //   Sleep:          sleep.interval.end_time (UTC, RFC-3339) — sleep is session-typed, only end_time supported
        //   Sample:         {stem}.sample_time.physical_time (UTC, RFC-3339)
        //   Daily:          {stem}.date (string "yyyy-MM-dd")
        // Reference: https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/list
        val stem = dataType.replace('-', '_')
        val startText = ISO_FMT.format(window.first)
        val endText = ISO_FMT.format(window.second)
        return when (timeAxisFor(dataType)) {
            TimeAxis.INTERVAL ->
                "$stem.interval.start_time >= \"$startText\" AND " +
                    "$stem.interval.start_time < \"$endText\""
            TimeAxis.CIVIL_INTERVAL -> {
                val civilStart = CIVIL_DT_FMT.format(window.first)
                val civilEnd = CIVIL_DT_FMT.format(window.second)
                "$stem.interval.civil_start_time >= \"$civilStart\" AND " +
                    "$stem.interval.civil_start_time < \"$civilEnd\""
            }
            TimeAxis.SLEEP_INTERVAL ->
                // Sleep is session-typed: only end_time filtering is supported (not start_time).
                // We filter by when the sleep session *ended* within the window.
                "$stem.interval.end_time >= \"$startText\" AND " +
                    "$stem.interval.end_time < \"$endText\""
            TimeAxis.SAMPLE ->
                "$stem.sample_time.physical_time >= \"$startText\" AND " +
                    "$stem.sample_time.physical_time < \"$endText\""
            TimeAxis.DAILY -> {
                val startDate = window.first.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                // Google supports `>=` and `<` only for daily date filters. Advance endDate
                // by one day so a same-day window still matches: a PING interval that lives
                // entirely within 2026-04-17 becomes `date >= "2026-04-17" AND date < "2026-04-18"`.
                val endDate = window.second.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1)
                "$stem.date >= \"$startDate\" AND $stem.date < \"$endDate\""
            }
        }
    }

    private enum class TimeAxis { INTERVAL, CIVIL_INTERVAL, SLEEP_INTERVAL, SAMPLE, DAILY }

    private fun timeAxisFor(dataType: String): TimeAxis = when (dataType) {
        "steps", "altitude", "distance", "floors", "total-calories" ->
            TimeAxis.INTERVAL
        "exercise" ->
            TimeAxis.CIVIL_INTERVAL
        "sleep" ->
            TimeAxis.SLEEP_INTERVAL
        "heart-rate", "heart-rate-variability", "oxygen-saturation",
        "respiratory-rate-sleep-summary", "weight", "body-fat" ->
            TimeAxis.SAMPLE
        "daily-resting-heart-rate", "daily-sleep-temperature-derivations" ->
            TimeAxis.DAILY
        else -> TimeAxis.INTERVAL
    }

    private fun widenInterval(interval: PingInterval, overlap: Duration): Pair<Instant, Instant> {
        return interval.physicalStartTime.minus(overlap) to interval.physicalEndTime.plus(overlap)
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

    private fun normalizedBaseUrl(raw: String): HttpUrl {
        val trimmed = if (raw.endsWith("/")) raw else "$raw/"
        return trimmed.toHttpUrl()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleHealthApiService::class.java)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ISO_FMT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        // Civil (local) datetime format — no timezone suffix; used for sleep.interval.civil_start_time
        private val CIVIL_DT_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(java.time.ZoneOffset.UTC)
        // Google's default pageSize is 1440 for most data types; exercise/sleep cap at 25 per response.
        // Max is 10000. 1000 is a safe middle ground: most daily windows fit in one response,
        // exercise/sleep get silently truncated to 25 anyway (expected).
        // Reference: https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/list
        private const val PAGE_SIZE = 1000
        private const val MAX_CONCURRENT_PER_USER = 3
        private const val MAX_RATE_LIMIT_RETRIES = 3
        // 5xx retries: 2s + 4s + 8s + 16s + 32s ≈ 62s cumulative before throwing, survives typical
        // transient Google outages without blocking the worker pool for minutes.
        private const val MAX_5XX_RETRIES = 5
        private const val MAX_ERROR_BODY_BYTES = 4096L
        private val DEDUP_TTL: Duration = Duration.ofMinutes(5)
        private val OVERLAP: Duration = Duration.ofMinutes(2)
        private val RATE_LIMIT_INITIAL_BACKOFF: Duration = Duration.ofSeconds(30)
        private val RATE_LIMIT_MAX_BACKOFF: Duration = Duration.ofMinutes(10)
        private val SERVER_ERROR_INITIAL_BACKOFF: Duration = Duration.ofSeconds(2)
        private val SERVER_ERROR_MAX_BACKOFF: Duration = Duration.ofSeconds(60)
    }
}



