package org.radarbase.push.integration.fitbit.service.subscription

import jakarta.inject.Named
import jakarta.ws.rs.core.Context
import okhttp3.OkHttpClient
import okio.IOException
import org.glassfish.jersey.server.monitoring.ApplicationEvent
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.DESTROY_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEvent.Type.INITIALIZATION_FINISHED
import org.glassfish.jersey.server.monitoring.ApplicationEventListener
import org.glassfish.jersey.server.monitoring.RequestEvent
import org.glassfish.jersey.server.monitoring.RequestEventListener
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.FITBIT_QUALIFIER
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class SubscriptionService(
    @Context private val config: Config,
    @Context private val httpClient: OkHttpClient,
    @Named(FITBIT_QUALIFIER) private val userRepository: FitbitUserRepository
) : ApplicationEventListener {

    private val requestGenerator = SubscriptionRequestGenerator(config, userRepository)
    private val userExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val requestExecutorService = Executors.newSingleThreadExecutor()
    private val futures: MutableList<Future<*>> = mutableListOf()

    override fun onEvent(event: ApplicationEvent?) {
        when (event?.type) {
            INITIALIZATION_FINISHED -> start()
            DESTROY_FINISHED -> stop()
            else -> logger.info("Application Event Received: ${event?.type}")
        }
    }

    override fun onRequest(requestEvent: RequestEvent?): RequestEventListener? = null

    private fun start() {
        logger.info("Application Initialisation completed. Starting Subscription service...")
        userExecutorService.scheduleAtFixedRate(::iterateUsers, 1, 5, TimeUnit.MINUTES)
    }

    private fun stop() {
        logger.info("Application destroy completed. Stopping subscription service...")
        userExecutorService.awaitTermination(30, TimeUnit.SECONDS)
        futures.forEach { it.cancel(true) }
        futures.clear()
        userRepository.stream()
            .map { user ->
                requestExecutorService.submit {
                    makeDeletionRequest(requestGenerator.subscriptionDeletionRequest(user))
                }
            }
    }

    private fun iterateUsers() {
        if (!futures.all { it.isDone }) {
            logger.info("The previous task is already running. Waiting for next iteration")
            // wait for the next iteration
            return
        }
        futures.clear()
        logger.info("Iterate Fitbit users and create subscription for newly added ones...")
        try {
            futures += userRepository.stream()
                .map { user ->
                    requestExecutorService.submit {
                        makeCreationRequest(requestGenerator.subscriptionCreationRequest(user))
                    }
                }
        } catch (exc: IOException) {
            logger.warn("I/O Exception while iterating Fitbit users.", exc)
        } catch (exc: Throwable) {
            logger.warn("Error while iterating Fitbit users.", exc)
        }
    }

    private fun makeCreationRequest(request: SubscriptionRequest?) {
        if (request == null) {
            return
        }
        logger.debug("Making Request: {}", request.request)
        try {
            httpClient.newCall(request.request).execute().use { response ->
                if (response.isSuccessful) {
                    requestGenerator.subscriptionCreationRequestSuccessful(request, response)
                } else {
                    requestGenerator.subscriptionCreationRequestFailed(request, response)
                }
            }
        } catch (ex: Throwable) {
            logger.warn("Error making request ${request.request.url}.", ex)
        }
    }

    private fun makeDeletionRequest(request: SubscriptionRequest?) {
        if (request == null) {
            return
        }
        logger.debug("Making Request: {}", request.request)
        try {
            httpClient.newCall(request.request).execute().use { response ->
                if (response.isSuccessful) {
                    requestGenerator.subscriptionDeletionRequestSuccessful(request, response)
                } else {
                    requestGenerator.subscriptionDeletionRequestFailed(request, response)
                }
            }
        } catch (exc: Throwable) {
            logger.warn("Error making request ${request.request.url}.", exc)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionService::class.java)
    }
}