package org.radarbase.push.integration.fitbit.redis

import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.radarbase.push.integration.common.redis.RedisHolder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.time.Instant

/**
 * Accesses a OffsetRange json object a Redis entry.
 */
class OffsetRedisPersistence(
    private val redisHolder: RedisHolder
) : OffsetPersistenceFactory {

    override fun read(path: String): Offsets? {
        return try {
            redisHolder.execute { redis ->
                redis[path]?.let { value ->
                    redisOffsetReader.readValue<RedisOffsets>(value)
                        .offsets
                        .fold(Offsets()) { set, (userId, route, lastSuccessOffset, latestOffset) ->
                            set.apply {
                                add(
                                    UserRouteOffset(
                                        userId,
                                        route,
                                        lastSuccessOffset,
                                        latestOffset
                                    )
                                )
                            }
                        }
                }
            }
        } catch (ex: IOException) {
            logger.error(
                "Error reading offsets from Redis: {}. Processing all offsets.",
                ex.toString()
            )
            null
        }
    }

    /**
     * Read the specified Path in Redis and adds the given UserRouteOffset to the offsets.
     */
    override fun add(path: Path, offset: UserRouteOffset) {
        val offsets: Offsets = (read(path.toString()) ?: Offsets()).apply { add(offset)}
        val redisOffsets = RedisOffsets(offsets.offsetsMap.map { (userRoute, fitbitOffsets) ->
            RedisOffset(
                userRoute.userId,
                userRoute.route,
                fitbitOffsets.lastSuccessOffset,
                fitbitOffsets.latestOffset
            )
        })
        try {
            redisHolder.execute { redis ->
                redis.set(path.toString(), redisOffsetWriter.writeValueAsString(redisOffsets))
            }
        } catch (e: IOException) {
            logger.error("Failed to write offsets to Redis: {}", e.toString())
        }
    }

    companion object {
        data class RedisOffsets(
            val offsets: List<RedisOffset>
        )

        data class RedisOffset(
            val userId: String,
            val route: String,
            val lastSuccessOffset: Instant,
            val latestOffset: Instant
        )

        private val logger = LoggerFactory.getLogger(OffsetRedisPersistence::class.java)
        private val mapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
        val redisOffsetWriter: ObjectWriter = mapper.writerFor(RedisOffsets::class.java)
        val redisOffsetReader: ObjectReader = mapper.readerFor(RedisOffsets::class.java)
    }
}
