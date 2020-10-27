package org.radarbase.push.integrations.garmin.service

import com.fasterxml.jackson.databind.JsonNode
import org.jvnet.hk2.annotations.Service
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integrations.common.user.User
import org.radarbase.push.integrations.garmin.converter.ActivitiesGarminAvroConverter
import org.radarbase.push.integrations.garmin.converter.DailiesGarminAvroConverter
import org.radarbase.push.integrations.garmin.user.UserRepository
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.inject.Singleton
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

@Singleton
@Service
class GarminHealthApiService(
    @Context val userRepository: UserRepository,
    @Context private val producerPool: ProducerPool,
    @Context private val config: Config,
    garminConfig: GarminConfig = config.pushIntegrationConfig.garminConfig
) {
    private val dailiesConverter =
        DailiesGarminAvroConverter(garminConfig.dailiesTopicName)

    private val activitiesConverter =
        ActivitiesGarminAvroConverter(garminConfig.activitiesTopicName)

    @Throws(IOException::class, BadRequestException::class)
    fun processDailies(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = dailiesConverter.convert(tree, request)
        producerPool.produce(dailiesConverter.topic, records)
        return Response.status(Response.Status.OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processActivities(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = activitiesConverter.convert(tree, request)
        producerPool.produce(activitiesConverter.topic, records)
        return Response.status(Response.Status.OK).build()
    }

    fun processActivityDetails(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processManualActivities(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processEpochs(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processSleeps(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO()
    }

    fun processBodyCompositions(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processStress(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processUserMetrics(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processMoveIQ(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processPulseOx(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    fun processRespiration(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class, NoSuchElementException::class, BadRequestException::class)
    fun handleDeregistration(userId: String?, userAccessToken: String?): Response {
        if (userId.isNullOrBlank()) {
            throw BadRequestException("Invalid userId for degregistration")
        }
        userRepository.reportDeregistration(userRepository.findByExternalId(userId))
        return Response.status(Response.Status.OK).build()
    }

    /**
     * Finds [User] using [User.externalUserId]
     *
     * @throws IOException            if there was an error when finding the user.
     * @throws NoSuchElementException if the user does not exists in this repository.
     */
    @Throws(NoSuchElementException::class, IOException::class)
    fun UserRepository.findByExternalId(externalId: String): User {
        return stream()
            .filter { user: User -> user.externalUserId == externalId }
            .findFirst()
            .orElseGet { throw NoSuchElementException("User not found in the User repository") }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(GarminHealthApiService::class.java)
    }
}
