package org.radarbase.push.integration.garmin.service

import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Named
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.OK
import org.radarbase.gateway.Config
import org.radarbase.gateway.GarminConfig
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.converter.*
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import java.io.IOException

class GarminHealthApiService(
    @Named(GARMIN_QUALIFIER) private val userRepository: GarminUserRepository,
    @Context private val producerPool: ProducerPool,
    @Context private val config: Config
) {
    private val garminConfig: GarminConfig = config.pushIntegration.garmin

    private val dailiesConverter =
        DailiesGarminAvroConverter(garminConfig.dailiesTopicName)

    private val activitiesConverter =
        ActivitiesGarminAvroConverter(garminConfig.activitiesTopicName)

    private val manualActivitiesConverter =
        ManualActivitiesGarminAvroConverter(garminConfig.activitiesTopicName)

    private val activityDetailsConverter =
        ActivityDetailsGarminAvroConverter(garminConfig.activityDetailsTopicName)

    private val epochsConverter = EpochsGarminAvroConverter(garminConfig.epochSummariesTopicName)

    private val sleepSummaryConverter = SleepsGarminAvroConverter(garminConfig.sleepsTopicName)

    private val bodyCompsConverter =
        BodyCompGarminAvroConverter(garminConfig.bodyCompositionsTopicName)

    private val stressConverter = StressDetailsGarminAvroConverter(garminConfig.stressTopicName)

    private val userMetricsConverter =
        UserMetricsGarminAvroConverter(garminConfig.userMetricsTopicName)

    private val moveIQConverter = MoveIQGarminAvroConverter(garminConfig.moveIQTopicName)

    private val pulseOxConverter = PulseOxGarminAvroConverter(garminConfig.pulseOXTopicName)

    private val respirationConverter =
        RespirationGarminAvroConverter(garminConfig.respirationTopicName)

    private val activityDetailsSampleConverter = ActivityDetailsSampleGarminAvroConverter(
        garminConfig.activityDetailsSampleTopicName
    )

    private val stressBodyBatteryConverter = StressBodyBatteryGarminAvroConverter(
        garminConfig.bodyBatterySampleTopicName
    )

    private val heartRateSampleConverter = HeartRateSampleGarminAvroConverter(
        garminConfig.heartRateSampleConverter
    )

    private val sleepLevelConverter = SleepLevelGarminAvroConverter(
        garminConfig.sleepLevelTopicName
    )

    private val sleepPulseOxConverter =
        SleepPulseOxGarminAvroConverter(garminConfig.pulseOXTopicName)

    private val sleepRespirationConverter =
        SleepRespirationGarminAvroConverter(garminConfig.respirationTopicName)

    private val stressLevelConverter = StressLevelGarminAvroConverter(
        garminConfig.stressLevelTopicName
    )

    @Throws(IOException::class, BadRequestException::class)
    fun processDailies(tree: JsonNode, user: User): Response {
        val records = dailiesConverter.validateAndConvert(tree, user)
        producerPool.produce(dailiesConverter.topic, records)

        val samples = heartRateSampleConverter.validateAndConvert(tree, user)
        producerPool.produce(heartRateSampleConverter.topic, samples)

        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processActivities(tree: JsonNode, user: User): Response {
        val records = activitiesConverter.validateAndConvert(tree, user)
        producerPool.produce(activitiesConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processActivityDetails(tree: JsonNode, user: User): Response {
        val records = activityDetailsConverter.validateAndConvert(tree, user)
        producerPool.produce(activityDetailsConverter.topic, records)

        val samples = activityDetailsSampleConverter.validateAndConvert(tree, user)
        producerPool.produce(activityDetailsSampleConverter.topic, samples)

        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processManualActivities(tree: JsonNode, user: User): Response {
        val records = manualActivitiesConverter.validateAndConvert(tree, user)
        producerPool.produce(manualActivitiesConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processEpochs(tree: JsonNode, user: User): Response {
        val records = epochsConverter.validateAndConvert(tree, user)
        producerPool.produce(epochsConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processSleeps(tree: JsonNode, user: User): Response {
        val records = sleepSummaryConverter.validateAndConvert(tree, user)
        producerPool.produce(sleepSummaryConverter.topic, records)

        val levels = sleepLevelConverter.validateAndConvert(tree, user)
        producerPool.produce(sleepLevelConverter.topic, levels)

        val pulseOx = sleepPulseOxConverter.validateAndConvert(tree, user)
        producerPool.produce(sleepPulseOxConverter.topic, pulseOx)

        val respiration = sleepRespirationConverter.validateAndConvert(tree, user)
        producerPool.produce(sleepRespirationConverter.topic, respiration)

        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processBodyCompositions(tree: JsonNode, user: User): Response {
        val records = bodyCompsConverter.validateAndConvert(tree, user)
        producerPool.produce(bodyCompsConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processStress(tree: JsonNode, user: User): Response {
        val records = stressConverter.validateAndConvert(tree, user)
        producerPool.produce(stressConverter.topic, records)

        val levels = stressLevelConverter.validateAndConvert(tree, user)
        producerPool.produce(stressLevelConverter.topic, levels)

        val bodyBattery = stressBodyBatteryConverter.validateAndConvert(tree, user)
        producerPool.produce(stressBodyBatteryConverter.topic, bodyBattery)

        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processUserMetrics(tree: JsonNode, user: User): Response {
        val records = userMetricsConverter.validateAndConvert(tree, user)
        producerPool.produce(userMetricsConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processMoveIQ(tree: JsonNode, user: User): Response {
        val records = moveIQConverter.validateAndConvert(tree, user)
        producerPool.produce(moveIQConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processPulseOx(tree: JsonNode, user: User): Response {
        val records = pulseOxConverter.validateAndConvert(tree, user)
        producerPool.produce(pulseOxConverter.topic, records)
        return Response.ok().build()
    }

    @Throws(IOException::class, BadRequestException::class)
    fun processRespiration(tree: JsonNode, user: User): Response {
        val records = respirationConverter.validateAndConvert(tree, user)
        producerPool.produce(respirationConverter.topic, records)
        return Response.ok().build()
    }
}
