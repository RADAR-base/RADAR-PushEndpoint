package org.radarbase.push.integration.fitbit.service.subscription

import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.EMPTY_REQUEST
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.redis.RedisHolder
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.redis.OffsetPersistenceFactory
import org.radarbase.push.integration.fitbit.redis.OffsetRedisPersistence
import org.radarbase.push.integration.fitbit.redis.UserRouteOffset
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionRequestGenerator(
    val config: Config, private val userRepository: FitbitUserRepository
) {
    private val userDataMap: ConcurrentHashMap<String, SubscriptionUserData> =
        ConcurrentHashMap<String, SubscriptionUserData>()
    private val subscriptionID: AtomicInteger = AtomicInteger(0)
    private val redisHolder: RedisHolder =
        RedisHolder(JedisPool(config.pushIntegration.fitbit.redis.uri))
    private val offsetPersistenceFactory: OffsetPersistenceFactory =
        OffsetRedisPersistence(redisHolder)

    private fun subscriptionUrl(user: User, subscriptionID: String): String {
        return "https://api.fitbit.com/1/user/" + user.serviceUserId + "/apiSubscriptions/" + subscriptionID + ".json"
    }

    fun subscriptionCreationRequest(user: User): SubscriptionRequest? {
        if (userDataMap[user.id] == null) {
            userDataMap[user.id] = SubscriptionUserData(
                false,
                subscriptionID.getAndIncrement().toString(),
                Instant.now()
            )
        }
        val userData = userDataMap[user.id] ?: return null
        if (userData.subscriptionStatus) return null
        if (Instant.now().isBefore(userData.nextRequestTime)) return null
        return SubscriptionRequest(
            Request.Builder().url(subscriptionUrl(user, userData.subscriptionID))
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + userRepository.getAccessToken(user))
                .addHeader(
                    "X-Fitbit-Subscriber-Id",
                    config.pushIntegration.fitbit.subscriptionConfig.subscriberID
                ).post(EMPTY_REQUEST).build(), user, userData.subscriptionID
        )
    }

    fun subscriptionDeletionRequest(user: User): SubscriptionRequest? {
        val userData = userDataMap[user.id] ?: return null
        if (!userData.subscriptionStatus) return null
        return SubscriptionRequest(
            Request.Builder().url(subscriptionUrl(user, userData.subscriptionID))
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + userRepository.getAccessToken(user))
                .addHeader(
                    "X-Fitbit-Subscriber-Id",
                    config.pushIntegration.fitbit.subscriptionConfig.subscriberID
                ).delete(EMPTY_REQUEST).build(), user, userData.subscriptionID
        )
    }

    fun subscriptionCreationRequestSuccessful(request: SubscriptionRequest, response: Response) {
        userDataMap[request.user.userId]?.subscriptionStatus = true

        request.user.externalId?.let {
            offsetPersistenceFactory.add(
                Path.of(it),
                UserRouteOffset(it, "activities", Instant.now(), Instant.now())
            )
            offsetPersistenceFactory.add(
                Path.of(it),
                UserRouteOffset(it, "body", Instant.now(), Instant.now())
            )
            offsetPersistenceFactory.add(
                Path.of(it),
                UserRouteOffset(it, "foods", Instant.now(), Instant.now())
            )
            offsetPersistenceFactory.add(
                Path.of(it),
                UserRouteOffset(it, "sleep", Instant.now(), Instant.now())
            )
        }

        logger.info("Request successful: {}. Response: {}", request.request, response)
    }

    fun subscriptionCreationRequestFailed(request: SubscriptionRequest, response: Response) {
        when (response.code) {
            429 -> {
                logger.info("Too many requests reach rate limit.")
                userDataMap[request.user.userId]?.nextRequestTime = Instant.now() + BACK_OFF_TIME
            }

            409 -> logger.info("The given user is already subscribed to this stream using a different subscription ID or the given subscription ID is already used to identify a subscription to a different stream.")
            else -> logger.info("Request failed, {} {}", request, response)
        }
    }

    fun subscriptionDeletionRequestSuccessful(request: SubscriptionRequest, response: Response) {
        logger.info("Request successful: {}. Response: {}", request.request, response)
    }

    fun subscriptionDeletionRequestFailed(request: SubscriptionRequest, response: Response) {
        logger.info("Request failed, {} {}", request, response)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionRequestGenerator::class.java)
        private val BACK_OFF_TIME = Duration.ofMinutes(1L)
    }
}