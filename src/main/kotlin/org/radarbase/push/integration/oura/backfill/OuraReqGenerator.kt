package org.radarbase.push.integration.oura.backfill

import okhttp3.Response
import org.radarbase.gateway.Config
import org.radarbase.oura.route.Route
import org.radarbase.oura.user.User
import org.radarbase.push.integration.garmin.backfill.route.*
import org.radarbase.push.integration.oura.offset.OuraRedisOffsetManager
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.radarbase.push.integration.oura.user.OuraUserRepository
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.radarbase.push.integration.garmin.util.offset.*
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import org.radarbase.oura.request.OuraRequestGenerator
import org.radarbase.oura.request.RestRequest
import org.radarbase.push.integration.garmin.backfill.TooManyRequestsException

class OuraReqGenerator(
    val config: Config,
    private val userRepository: OuraUserRepository,
    private val redisHolder: RedisHolder =
        RedisHolder(JedisPool(config.pushIntegration.garmin.backfill.redis.uri)),
    private val offsetPersistenceFactory: OffsetPersistenceFactory =
        OffsetRedisPersistence(redisHolder),
    private val defaultQueryRange: Duration = Duration.ofDays(15),
) {

    private val ouraOffsetManager = OuraRedisOffsetManager(redisUri = config.pushIntegration.garmin.backfill.redis.uri, redisHolder, offsetPersistenceFactory)

    private var ouraRequestGenerator: OuraRequestGenerator = OuraRequestGenerator(userRepository = userRepository, ouraOffsetManager = ouraOffsetManager);

    private val userNextRequest: MutableMap<String, Instant> = mutableMapOf()

    private var nextRequestTime: Instant = Instant.MIN

    private val shouldBackoff: Boolean
        get() = Instant.now() < nextRequestTime

    fun requests(user: User, max: Int): Sequence<RestRequest> {
        val requests = ouraRequestGenerator.requests(user, max)
        return requests
    }

    fun requestSuccessful(request: RestRequest, response: Response) {
        val records = ouraRequestGenerator.requestSuccessful(request, response)
        logger.info(records.toString())
    }

    fun requestFailed(request: RestRequest, response: Response) {
        ouraRequestGenerator.requestFailed(request, response)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OuraReqGenerator::class.java)
        private val BACK_OFF_TIME = Duration.ofMinutes(1L)
        private val USER_BACK_OFF_TIME = Duration.ofDays(1L)
    }
}
