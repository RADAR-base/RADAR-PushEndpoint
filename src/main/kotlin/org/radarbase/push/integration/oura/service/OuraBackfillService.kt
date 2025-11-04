package org.radarbase.push.integration.oura.service

import jakarta.inject.Named
import jakarta.ws.rs.core.Context
import okhttp3.OkHttpClient
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.DESTROY_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.INITIALIZATION_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.OURA_QUALIFIER
import org.radarbase.push.integration.garmin.backfill.GarminRequestGenerator
import org.radarbase.oura.request.RestRequest
import org.radarbase.push.integration.garmin.backfill.TooManyRequestsException
import org.radarbase.push.integration.oura.backfill.OuraReqGenerator
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.radarbase.push.integration.oura.user.OuraUserRepository
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.radarbase.push.integration.garmin.util.RedisRemoteLockManager
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * The backfill service should be used to collect historic data. This will send requests to garmin's
 * service to create POST requests for historic data to our server.
 */
class OuraBackfillService(
    @Context private val config: Config,
    @Context private val httpClient: OkHttpClient,
    @Named(OURA_QUALIFIER) private val userRepository: OuraUserRepository
) : ApplicationEventListener {

    private val redisHolder =
        RedisHolder(JedisPool(config.pushIntegration.garmin.backfill.redis.uri))
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private val requestExecutorService = Executors.newFixedThreadPool(
        config.pushIntegration.garmin.backfill.maxThreads
    )
    private val requestGenerator = OuraReqGenerator(config, userRepository)
    private val remoteLockManager = RedisRemoteLockManager(
        redisHolder,
        config.pushIntegration.garmin.backfill.redis.lockPrefix
    )

    private val requestsPerUserPerIteration: Int
        get() = config.pushIntegration.garmin.backfill.requestsPerUserPerIteration

    private val futures: MutableList<Future<*>> = mutableListOf()

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            INITIALIZATION_FINISHED -> start()
            DESTROY_FINISHED -> stop()
            else -> logger.info("Application event received: ${event?.type}")
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun start() {
        logger.info("Application Initialisation completed. Starting Backfill service...")

        executorService.scheduleAtFixedRate(
            ::iterateUsers,
            0,
            config.pushIntegration.garmin.backfill.iterationIntervalMinutes,
            TimeUnit.MINUTES
        )
    }

    private fun stop() {
        logger.info("Application Destroy completed. Stopping Backfill service...")
        try {
            requestExecutorService.awaitTermination(30, TimeUnit.SECONDS)
            executorService.awaitTermination(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.error("Failed to complete execution: interrupted")
        }
    }

    private fun iterateUsers() {
        if (!futures.all { it.isDone }) {
            logger.info("The previous task is already running. Waiting for next iteration")
            // wait for the next iteration
            return
        }
        futures.clear()
        logger.info("Making Oura Backfill requests...")
        try {
            futures += userRepository.stream()
                .map { user ->
                    requestExecutorService.submit {
                        remoteLockManager.tryRunLocked(user.versionedId) {
                            requestGenerator.requests(user, requestsPerUserPerIteration)
                                .forEach { req -> makeRequest(req) }
                        }
                    }
                }
        } catch (exc: IOException) {
            logger.warn("I/O Exception while making Backfill requests.", exc)
        } catch (ex: Throwable) {
            logger.warn("Error Making Oura Backfill requests.", ex)
        }
    }

    private fun makeRequest(req: RestRequest) {
        logger.info("Making Request: {}", req.request)
        try {
            httpClient.newCall(req.request).execute().use { response ->
                if (response.isSuccessful) {
                    requestGenerator.requestSuccessful(req, response)
                } else {
                    try {
                        requestGenerator.requestFailed(req, response)
                    } catch (ex: TooManyRequestsException) {
                        futures.forEach { it.cancel(true) }
                        futures.clear()
                    }
                }
            }
        } catch (ex: Throwable) {
            logger.warn("Error making request ${req.request.url}.", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OuraBackfillService::class.java)
    }
}
