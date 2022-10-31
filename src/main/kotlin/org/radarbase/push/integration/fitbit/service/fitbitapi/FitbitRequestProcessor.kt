package org.radarbase.push.integration.fitbit.service.fitbitapi

import jakarta.inject.Named
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.core.Context
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator
import org.radarbase.push.integration.common.redis.RedisHolder
import org.radarbase.push.integration.common.redis.RedisRemoteLockManager
import org.radarbase.push.integration.fitbit.converter.TopicData
import org.radarbase.push.integration.fitbit.request.FitbitRequestGenerator
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FitbitRequestProcessor(
    @Context private val config: Config,
    @Context @Named(DelegatedAuthValidator.FITBIT_QUALIFIER) private val userRepository: FitbitUserRepository,
    @Context private val producerPool: ProducerPool
) : ApplicationEventListener {
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private val requestExecutorService = Executors.newFixedThreadPool(
        config.pushIntegration.fitbit.requestMaxThreads
    )

    private val redisHolder = RedisHolder(JedisPool(config.pushIntegration.garmin.backfill.redis.uri))

    private val requestGenerator = FitbitRequestGenerator(userRepository, config, producerPool)
    private val remoteLockManager = RedisRemoteLockManager(
        redisHolder, config.pushIntegration.fitbit.redis.lockPrefix
    )

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            ApplicationEvent.Type.INITIALIZATION_FINISHED -> start()
            ApplicationEvent.Type.DESTROY_FINISHED -> stop()
            else -> logger.info("Application event received: ${event?.type}")
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun start() {
        logger.info("Application Initialisation completed. Starting Backfill service...")

        executorService.scheduleAtFixedRate(::makeRequests, 1, 5, TimeUnit.MINUTES)
    }

    private fun makeRequests() {
        var requestsGenerated: Long = 0
        val timeout = ChronoUnit.MILLIS.between(Instant.now(), requestGenerator.timeOfNextRequest)
        if (timeout > 0) {
            logger.info("Waiting {} milliseconds for next available request", timeout)
            Thread.sleep(timeout)
        }

        requestGenerator.requests().associateBy { it.user }.forEach { requestMap ->
            requestsGenerated++
            requestExecutorService.submit {
                remoteLockManager.tryRunLocked(requestMap.key.versionedId) {
                    if (!requestMap.value.isStillValid) {
                        logger.info("Requesting {}", requestMap.value.getRequest().url)
                        val records = makeRequest(requestMap.value)
                        records?.let {
                            logger.debug("Processed ${it.count()} records")
                        }
                    }
                }
            }
        }
        logger.info("Processed $requestsGenerated Urls")
    }

    private fun makeRequest(request: FitbitRestRequest): Sequence<Result<TopicData>>? {
        return try {
            request.handleRequest()
        } catch (ex: IOException) {
            logger.warn("Failed to make request: {}", ex.toString())
            null
        } catch (ex: NotAuthorizedException) {
            logger.warn("Failed to make request: {}", ex.toString())
            null
        }
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


    companion object {
        private val logger = LoggerFactory.getLogger(FitbitRequestProcessor::class.java)
    }

}
