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

class FitbitApiService(
    @Context private val config: Config,
    @Context objectMapper: ObjectMapper
) {
    private val fitbitConfig: FitbitConfig = config.pushIntegration.fitbit
    private val contentReader =
        objectMapper.readerFor(object : TypeReference<List<FitbitNotification>>() {})

    private val redisHolder : RedisHolder = RedisHolder(JedisPool(config.pushIntegration.fitbit.redis.uri))
    private val offsetPersistenceFactory: OffsetPersistenceFactory = OffsetRedisPersistence(redisHolder)

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
            val offsets: Offsets? = offsetPersistenceFactory.read(it.ownerId)

            when (it.collectionType) {
                "userRevokedAccess", "deleteUser" -> logger.warn("The user has restricted to send data.")
                "activities" -> processActivity(it)
                "body" -> processBody(it)
                "foods" -> processNutrition(it)
                "sleep" -> processSleep(it)
                else -> logger.info("Unsupported collectionType {} is received.", it.collectionType)
            }
        }

        return Response.noContent().build()
    }

    fun processActivity(activity: FitbitNotification) {
        val offsets: Offsets? = offsetPersistenceFactory.read(activity.ownerId)
        if (offsets != null) {
            val fitbitOffsets = offsets.offsetsMap[UserRoute(activity.ownerId, "activities")]
        }
    }

    fun processBody(body: FitbitNotification) {}

    fun processNutrition(nutrition: FitbitNotification) {}

    fun processSleep(sleep: FitbitNotification) {}



    companion object {
        private val logger = LoggerFactory.getLogger(FitbitApiService::class.java)
    }
}
