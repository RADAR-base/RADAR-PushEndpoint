package org.radarbase.push.integration.fitbit.service.fitbitapi

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status
import org.radarbase.gateway.Config
import org.radarbase.gateway.FitbitConfig
import org.radarbase.push.integration.common.redis.RedisHolder
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.dto.FitbitNotification
import org.radarbase.push.integration.fitbit.redis.*
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FitbitApiService(
    @Context private val config: Config,
    @Context objectMapper: ObjectMapper
) {
    private val fitbitConfig: FitbitConfig = config.pushIntegration.fitbit
    private val contentReader =
        objectMapper.readerFor(object : TypeReference<List<FitbitNotification>>() {})

    private val redisHolder: RedisHolder =
        RedisHolder(JedisPool(config.pushIntegration.fitbit.redis.uri))
    private val offsetPersistenceFactory: OffsetPersistenceFactory =
        OffsetRedisPersistence(redisHolder)

    @Throws(IOException::class, BadRequestException::class)
    fun verifySubscriber(verificationCode: String): Response {
        if (verificationCode == fitbitConfig.verificationCode) {
            return Response.noContent().build()
        }
        return Response.status(Status.NOT_FOUND).build()
    }


    fun addNotifications(user: User, contents: JsonNode): Response {
        val notifications = contentReader.readValue<List<FitbitNotification>>(contents)

        notifications.forEach {

            when (it.collectionType) {
                "userRevokedAccess", "deleteUser" -> logger.warn("The user has restricted to send data.")
                "activities","body","foods","sleep" -> processNotification(it, user)
                else -> logger.info("Unsupported collectionType {} is received.", it.collectionType)
            }
        }

        return Response.noContent().build()
    }

    fun processNotification(notification: FitbitNotification, user: User){
        val offsets: Offsets? = offsetPersistenceFactory.read(user.versionedId)
        offsetPersistenceFactory.add(
            Path.of(user.versionedId),
            UserRouteOffset(
                user.versionedId,
                notification.collectionType,
                offsets?.offsetsMap?.get(UserRoute(user.versionedId, notification.collectionType))?.lastSuccessOffset
                    ?: user.startDate,
                convertStringToInstant(notification.date)
            )
        )
    }

    private fun convertStringToInstant(date: String): Instant {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return LocalDateTime.parse(date, formatter).toInstant(ZoneOffset.UTC)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FitbitApiService::class.java)
    }
}
