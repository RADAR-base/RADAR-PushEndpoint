package org.radarbase.push.integration.garmin.util

import org.radarbase.push.integration.common.redis.RedisHolder
import org.slf4j.LoggerFactory
import redis.clients.jedis.params.SetParams
import java.time.Duration
import java.util.*

class RedisRemoteLockManager(
    private val redisHolder: RedisHolder,
    private val keyPrefix: String
) : RemoteLockManager {
    private val uuid: String = UUID.randomUUID().toString()

    init {
        logger.info("Managing locks as ID {}", uuid)
    }

    override fun acquireLock(name: String): RemoteLockManager.RemoteLock? {
        val lockKey = "$keyPrefix/$name.lock"
        return redisHolder.execute { redis ->
            redis.set(lockKey, uuid, setParams)?.let {
                RemoteLock(lockKey)
            }
        }
    }

    private inner class RemoteLock(
            private val lockKey: String
    ) : RemoteLockManager.RemoteLock {
        override fun close() {
            return redisHolder.execute { redis ->
                if (redis.get(lockKey) == uuid) {
                    redis.del(lockKey)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisRemoteLockManager::class.java)
        private val setParams = SetParams()
                .nx() // only set if not already set
                .px(Duration.ofDays(1).toMillis()) // limit the duration of a lock to 24 hours
    }
}
