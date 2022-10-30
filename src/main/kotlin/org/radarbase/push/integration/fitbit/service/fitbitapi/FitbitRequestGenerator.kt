package org.radarbase.push.integration.fitbit.service.fitbitapi

import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.redis.RedisHolder
import org.radarbase.push.integration.fitbit.redis.OffsetPersistenceFactory
import org.radarbase.push.integration.fitbit.redis.OffsetRedisPersistence
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository
import redis.clients.jedis.JedisPool

class FitbitRequestGenerator(
    val config: Config,
    private val userRepository: FitbitUserRepository,
    private val redisHolder: RedisHolder = RedisHolder(JedisPool(config.pushIntegration.fitbit.redis.uri)),
    private val offsetPersistenceFactory: OffsetPersistenceFactory = OffsetRedisPersistence(redisHolder)
) {

}