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

package org.radarbase.push.integration.google.subscriptions

import jakarta.inject.Named
import jakarta.ws.rs.core.Context
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.DESTROY_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.INITIALIZATION_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.googlehealth.user.GoogleHealthUserRepository
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GOOGLE_HEALTH_QUALIFIER
import org.radarbase.push.integration.garmin.util.RedisRemoteLockManager
import org.radarbase.push.integration.google.subscriptions.model.RemoteSubscription
import org.radarbase.push.integration.google.subscriptions.model.SubscriptionResult
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.mapNotNullTo

class GoogleHealthSubscriptionReconcileService(
    @param:Named(GOOGLE_HEALTH_QUALIFIER) private val userRepository: GoogleHealthUserRepository,
    @param:Context private val subscriptionService: GoogleHealthSubscriptionService,
    @param:Context private val locks: RedisRemoteLockManager,
    @Context config: Config,
) : ApplicationEventListener {

    private val ghConfig = config.pushIntegration.googlehealth
    private val enabled = ghConfig.subscriptionReconcileEnabled
    private val intervalMinutes = ghConfig.subscriptionReconcileIntervalMinutes
    private val maxDeletesPerPass = ghConfig.subscriptionReconcileMaxDeletesPerPass
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            INITIALIZATION_FINISHED -> start()
            DESTROY_FINISHED -> stop()
            else -> { /* no-op */
            }
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun start() {
        if (!enabled) {
            logger.info("Google Health subscription reconcile disabled by config. Skipping scheduler start.")
            return
        }
        if (!subscriptionService.isConfigured) {
            logger.warn("Service account not configured — subscription reconcile will not run.")
            return
        }
        logger.info(
            "Starting Google Health subscription reconcile (firstRunDelay={}s, intervalMinutes={}).",
            RECONCILE_INITIAL_DELAY_SECONDS, intervalMinutes,
        )
        scheduler.scheduleAtFixedRate(
            ::tick, RECONCILE_INITIAL_DELAY_SECONDS, intervalMinutes * 60, TimeUnit.SECONDS,
        )
    }

    private fun stop() {
        logger.info("Stopping Google Health subscription reconcile...")
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(30, TimeUnit.SECONDS)
        } catch (ex: InterruptedException) {
            logger.warn("Interrupted while stopping subscription reconcile", ex)
            Thread.currentThread().interrupt()
        }
    }

    private fun tick() {
        try {
            locks.tryRunLocked(RECONCILE_LOCK) { reconcile() }
        } catch (ex: Throwable) {
            logger.warn("Google Health subscription reconcile iteration failed", ex)
        }
    }

    private fun reconcile() {
        val authorized = try {
            userRepository.stream().toList()
        } catch (ex: IOException) {
            logger.warn("Reconcile skipped: could not fetch authorized users from Rest Sources", ex)
            return
        }

        val authorizedIds = authorized.mapTo(mutableSetOf()) { it.serviceUserId }
        val remote = try {
            subscriptionService.listSubscriptions()
        } catch (ex: Exception) {
            logger.warn("Reconcile skipped: could not list Google subscriptions", ex)
            return
        }

        val remoteIds = remote.mapNotNullTo(mutableSetOf()) { it.healthUserId }

        // Create subscriptions for authorized users that Google does not yet have.
        authorized.forEach { user ->
            if (user.serviceUserId !in remoteIds) {
                when (val result = subscriptionService.createSubscription(user.serviceUserId)) {
                    is SubscriptionResult.Success -> logger.info("Created subscription for user={}", user.serviceUserId)
                    else -> logger.warn("Reconcile create failed for user={}: {}", user.serviceUserId, result)
                }
            }
        }

        val desiredDataTypes = ghConfig.triggerDataTypes.toSet()
        remote.forEach { sub ->
            val userId = sub.healthUserId ?: return@forEach
            if (userId in authorizedIds && sub.dataTypeIds.toSet() != desiredDataTypes) {
                when (val result = subscriptionService.patchSubscription(sub.name, ghConfig.triggerDataTypes)) {
                    is SubscriptionResult.Success -> logger.info("Patched dataTypes for user={}", userId)
                    else -> logger.warn("Reconcile patch failed for user={}: {}", userId, result)
                }
            }
        }

        deleteStale(authorizedIds, remote)
    }

    /**
     * Deletes subscriptions whose user is not in the current authorized set. A withdrawn user is
     * removed from Rest Sources entirely (not returned as unauthorized), so they fall out of the
     * authorized set and their leftover subscription is cleaned up here.
     */
    private fun deleteStale(
        authorizedIds: Set<String>,
        remote: List<RemoteSubscription>,
    ): Pair<Int, Int> {
        val staleSubscriptions = remote.filter { it.healthUserId != null && it.healthUserId !in authorizedIds }

        if (maxDeletesPerPass > 0 && staleSubscriptions.size > maxDeletesPerPass) {
            logger.warn(
                "Reconcile would delete {} subscription(s), over the safety cap ({}). Skipping " +
                    "deletion this pass — investigate before mass-removal.",
                staleSubscriptions.size, maxDeletesPerPass,
            )
            return 0 to 0
        }

        var deleted = 0
        var deleteFailed = 0
        staleSubscriptions.forEach { sub ->
            when (val result = subscriptionService.deleteByName(sub.name)) {
                is SubscriptionResult.Success -> {
                    deleted++
                    logger.info("Reconcile deleted orphaned subscription user={} reason=deregistered", sub.healthUserId)
                }

                else -> {
                    deleteFailed++
                    logger.warn("Reconcile delete failed user={}: {}", sub.healthUserId, result)
                }
            }
        }
        return deleted to deleteFailed
    }

    companion object {
        private const val RECONCILE_LOCK = "googlehealth-subscription-reconcile"
        private const val RECONCILE_INITIAL_DELAY_SECONDS = 90L
        private val logger = LoggerFactory.getLogger(GoogleHealthSubscriptionReconcileService::class.java)
    }
}
