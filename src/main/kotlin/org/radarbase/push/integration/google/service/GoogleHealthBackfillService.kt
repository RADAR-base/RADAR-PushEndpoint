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

import jakarta.inject.Named
import jakarta.ws.rs.core.Context
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.DESTROY_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.INITIALIZATION_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GOOGLE_HEALTH_QUALIFIER
import org.radarbase.googlehealth.user.User
import org.radarbase.push.integration.garmin.util.RedisRemoteLockManager
import org.radarbase.push.integration.garmin.util.offset.OffsetRedisPersistence
import org.radarbase.push.integration.garmin.util.offset.UserRoute
import org.radarbase.push.integration.garmin.util.offset.UserRouteOffset
import org.radarbase.googlehealth.user.GoogleHealthUserRepository
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Scheduled loop that backfills **historical** Google Health data for authorized users.
 *
 * Per user, per enabled data type we advance a cursor in chunks of
 * [org.radarbase.gateway.GoogleHealthBackfillConfig.chunkSizeDays]. The cursor for a
 * (user, data type) pair is stored in Redis. A restart mid-backfill resumes from the last written chunk boundary.
 *
 * The `historical_cutoff` timestamp is captured once per user (first service to call
 * [GoogleHealthApiService.ensureHistoricalCutoff] writes it to Redis). Historical fetching itself
 * is done exclusively by this service, the PING path reads the cutoff only as the floor of its
 * live cursor so it never backfills.
 *
 * Per-user concurrency is bounded by [GoogleHealthApiService]'s semaphore.
 */
class GoogleHealthBackfillService(
    @param:Named(GOOGLE_HEALTH_QUALIFIER) private val userRepository: GoogleHealthUserRepository,
    @param:Context private val apiService: GoogleHealthApiService,
    @param:Context private val offsets: OffsetRedisPersistence,
    @param:Context private val locks: RedisRemoteLockManager,
    @Context config: Config,
    ) : ApplicationEventListener {

    private val ghConfig = config.pushIntegration.googlehealth
    private val bfConfig = ghConfig.backfill
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val workerPool = Executors.newFixedThreadPool(bfConfig.maxThreads)
    private val futures: MutableList<Future<*>> = mutableListOf()

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            INITIALIZATION_FINISHED -> start()
            DESTROY_FINISHED -> stop()
            else -> logger.debug("Application event received: {}", event?.type)
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun start() {
        if (!bfConfig.enabled) {
            logger.info("Google Health backfill disabled by config. Skipping scheduler start.")
            return
        }
        logger.info(
            "Starting Google Health backfill service (chunkSizeDays={}, iterationIntervalMinutes={}).",
            bfConfig.chunkSizeDays,
            bfConfig.iterationIntervalMinutes,
        )
        scheduler.scheduleAtFixedRate(
            ::iterate,
            1,
            bfConfig.iterationIntervalMinutes,
            TimeUnit.MINUTES,
        )
    }

    private fun stop() {
        logger.info("Stopping Google Health backfill service...")
        try {
            scheduler.shutdown()
            workerPool.shutdown()
            workerPool.awaitTermination(30, TimeUnit.SECONDS)
            scheduler.awaitTermination(30, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            logger.warn("Interrupted while stopping Google Health backfill service", ex)
            Thread.currentThread().interrupt()
        }
    }

    private fun iterate() {
        if (futures.any { !it.isDone }) {
            logger.info("Previous Google Health backfill iteration is still running; skipping.")
            return
        }
        futures.clear()
        try {
            userRepository.stream().forEach { user ->
                futures += workerPool.submit {
                    try {
                        locks.tryRunLocked(user.versionedId) { backfillOneUser(user) }
                    } catch (ex: Throwable) {
                        logger.warn(
                            "Error during Google Health backfill for user={}",
                            user.versionedId,
                            ex,
                        )
                    }
                }
            }
        } catch (ex: IOException) {
            logger.warn("I/O error while iterating users for Google Health backfill", ex)
        } catch (ex: Throwable) {
            logger.warn("Unexpected error during Google Health backfill iteration", ex)
        }
    }

    private fun backfillOneUser(user: User) {
        if (!user.isAuthorized) {
            logger.debug("Skipping unauthorized user {} for backfill", user.versionedId)
            return
        }
        for (dataType in ghConfig.enabledDataTypes) {
            try {
                backfillOneDataType(user, dataType)
            } catch (ex: Throwable) {
                logger.warn(
                    "Google Health backfill failed for user={} dataType={}",
                    user.versionedId,
                    dataType,
                    ex,
                )
            }
        }
    }

    private fun backfillOneDataType(user: User, dataType: String) {
        val route = GoogleHealthApiService.BACKFILL_ROUTE_PREFIX + dataType
        val storedOffset = offsets.read(user.versionedId)
            ?.offsetsMap?.get(UserRoute(user.versionedId, route))
        val earliestAllowed = Instant.now().minus(bfConfig.maxBackfillPeriod)
        var cursor = (storedOffset ?: user.startDate).coerceAtLeast(earliestAllowed)

        if (dataType in GoogleHealthApiService.NON_CHUNKED_TYPES) {
            val now = Instant.now()
            if (!cursor.isBefore(now)) return
            logger.info(
                "Backfilling user={} dataType={} (non-chunked) window=[{},{})",
                user.versionedId, dataType, cursor, now,
            )
            apiService.fetchAndPublishBlocking(user, dataType, cursor to now)
            offsets.add(Path.of(user.versionedId), UserRouteOffset(user.versionedId, route, now))
            return
        }

        // Historical cutoff is captured once per user and owned jointly with the PING path.
        // Backfill covers [user.startDate, cutoff]; PING path owns [cutoff, now]. Once cursor
        // reaches cutoff, backfill is permanently done for this (user, dataType).
        val upTo = apiService.ensureHistoricalCutoff(user)
        if (!cursor.isBefore(upTo)) {
            logger.debug(
                "Backfill complete for user={} dataType={} (cursor={} >= cutoff={})",
                user.versionedId,
                dataType,
                cursor,
                upTo,
            )
            return
        }

        val chunk = Duration.ofDays(bfConfig.chunkSizeDays)
        while (cursor.isBefore(upTo)) {
            val windowEnd = minOf(cursor.plus(chunk), upTo)
            logger.info(
                "Backfilling user={} dataType={} window=[{},{})",
                user.versionedId,
                dataType,
                cursor,
                windowEnd,
            )
            apiService.fetchAndPublishBlocking(user, dataType, cursor to windowEnd)
            offsets.add(
                Path.of(user.versionedId),
                UserRouteOffset(user.versionedId, route, windowEnd),
            )
            cursor = windowEnd
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleHealthBackfillService::class.java)
    }
}
