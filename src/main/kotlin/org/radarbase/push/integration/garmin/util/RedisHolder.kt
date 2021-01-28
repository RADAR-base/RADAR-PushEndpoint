package org.radarbase.push.integration.garmin.util

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisException
import java.io.Closeable
import java.io.IOException

class RedisHolder(private val jedisPool: JedisPool): Closeable {
    @Throws(IOException::class)
    fun <T> execute(routine: (Jedis) -> T): T {
        return try {
            jedisPool.resource.use {
                routine(it)
            }
        } catch (ex: JedisException) {
            throw IOException(ex)
        }
    }

    override fun close() {
        jedisPool.close()
    }
}
