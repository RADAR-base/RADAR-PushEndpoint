package org.radarbase.push.integration.garmin.service

import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.UserRepository
import org.radarbase.push.integration.garmin.converter.*
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.inject.Named
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.OK

class GarminHealthApiService(
    @Named(GARMIN_QUALIFIER) private val userRepository: UserRepository,
    @Context private val producerPool: ProducerPool,
    @Context private val config: Config
) {
    private val garminConfig: GarminConfig = config.pushIntegration.garmin

    private val dailiesConverter =
        DailiesGarminAvroConverter(garminConfig.dailiesTopicName)

    private val activitiesConverter =
        ActivitiesGarminAvroConverter(garminConfig.activitiesTopicName)

    private val activityDetailsConverter =
        ActivityDetailsGarminAvroConverter(garminConfig.activityDetailsTopicName)

    private val epochsConverter = EpochsGarminAvroConverter(garminConfig.epochSummariesTopicName)

    private val sleepsConverter = SleepsGarminAvroConverter(garminConfig.sleepsTopicName)

    private val bodyCompsConverter =
        BodyCompGarminAvroConverter(garminConfig.bodyCompositionsTopicName)

    private val stressConverter = StressDetailsGarminAvroConverter(garminConfig.stressTopicName)

    private val userMetricsConverter =
        UserMetricsGarminAvroConverter(garminConfig.userMetricsTopicName)

    private val moveIQConverter = MoveIQGarminAvroConverter(garminConfig.moveIQTopicName)

    @Throws(IOException::class, BadRequestException::class)
    fun processDailies(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = dailiesConverter.validateAndConvert(tree, request)
        producerPool.produce(dailiesConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processActivities(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = activitiesConverter.validateAndConvert(tree, request)
        producerPool.produce(activitiesConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processActivityDetails(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = activityDetailsConverter.validateAndConvert(tree, request)
        producerPool.produce(activityDetailsConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processManualActivities(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        return this.processActivities(tree, requestContext)
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processEpochs(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = epochsConverter.validateAndConvert(tree, request)
        producerPool.produce(epochsConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processSleeps(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = sleepsConverter.validateAndConvert(tree, request)
        producerPool.produce(sleepsConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processBodyCompositions(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = bodyCompsConverter.validateAndConvert(tree, request)
        producerPool.produce(bodyCompsConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processStress(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = stressConverter.validateAndConvert(tree, request)
        producerPool.produce(stressConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processUserMetrics(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = userMetricsConverter.validateAndConvert(tree, request)
        producerPool.produce(userMetricsConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processMoveIQ(tree: JsonNode, request: ContainerRequestContext): Response {
        val records = moveIQConverter.validateAndConvert(tree, request)
        producerPool.produce(moveIQConverter.topic, records)
        return Response.status(OK).build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processPulseOx(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processRespiration(tree: JsonNode, requestContext: ContainerRequestContext): Response {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class, NoSuchElementException::class, BadRequestException::class)
    fun handleDeregistration(userId: String?): Response {
        if (userId.isNullOrBlank()) {
            throw BadRequestException("Invalid userId for degregistration")
        }
        userRepository.reportDeregistration(userRepository.findByExternalId(userId))
        return Response.status(OK).build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GarminHealthApiService::class.java)
    }
}
