package org.radarbase.push.integration.garmin.util.offset

import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.radarbase.push.integration.garmin.util.PostponedWriter
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Accesses a OffsetRange json object a Redis entry.
 */
class OffsetRedisPersistence(
    private val redisHolder: RedisHolder
) : OffsetPersistenceFactory {

    override fun read(path: String): Offsets? {
        return try {
            redisHolder.execute { redis ->
                redis[path.toString()]?.let { value ->
                    redisOffsetReader.readValue<RedisOffsets>(value)
                        .offsets
                        .fold(Offsets(), { set, (userId, route, offset) ->
                            set.apply { add(UserRouteOffset(userId, route, offset)) }
                        })
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

    override fun writer(
        path: Path,
        startSet: Offsets?
    ): OffsetPersistenceFactory.Writer = RedisWriter(path, startSet)

    private inner class RedisWriter(
        private val path: Path,
        startSet: Offsets?
    ) : PostponedWriter("offsets", 1, TimeUnit.SECONDS),
        OffsetPersistenceFactory.Writer {
        override val offsets: Offsets = startSet ?: Offsets()

        override fun doWrite(): Unit = try {
            val offsets = RedisOffsets(offsets.offsetsMap.map { (userRoute, offset) ->
                RedisOffset(
                    userRoute.userId,
                    userRoute.route,
                    offset
                )
            })

            redisHolder.execute { redis ->
                redis.set(path.toString(), redisOffsetWriter.writeValueAsString(offsets))
            }
            Unit
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
            val offset: Instant
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
