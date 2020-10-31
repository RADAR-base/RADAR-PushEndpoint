package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminDailySummary
import java.io.IOException
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class DailiesGarminAvroConverter(topic: String = "push_integration_garmin_daily") :
    GarminAvroConverter(topic) {

    @Throws(IOException::class)
    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {
        val observationKey = observationKey(request)
        return tree[ROOT]
            .map { node -> Pair(observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): SpecificRecord {
        return GarminDailySummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            calendarDate = node["calendarDate"]?.asText()
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            steps = node["steps"]?.asInt()
            distanceInMeters = node["distanceInMeters"]?.asDouble()
            activeTimeInSeconds = node["activeTimeInSeconds"]?.asInt()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            bmrKilocalories = node["bmrKilocalories"]?.asInt()
            consumedCalories = node["consumedCalories"]?.asInt()
            moderateIntensityDurationInSeconds =
                node["moderateIntensityDurationInSeconds"]?.asInt()
            vigorousIntensityDurationInSeconds =
                node["vigorousIntensityDurationInSeconds"]?.asInt()
            floorsClimbed = node["floorsClimbed"]?.asInt()
            minHeartRateInBeatsPerMinute =
                node["minHeartRateInBeatsPerMinute"]?.asInt()
            averageHeartRateInBeatsPerMinute =
                node["averageHeartRateInBeatsPerMinute"]?.asInt()
            maxHeartRateInBeatsPerMinute =
                node["maxHeartRateInBeatsPerMinute"]?.asInt()
            restingHeartRateInBeatsPerMinute =
                node["restingHeartRateInBeatsPerMinute"]?.asInt()
            averageStressLevel = node["averageStressLevel"]?.asInt()
            maxStressLevel = node["maxStressLevel"]?.asInt()
            stressDurationInSeconds = node["stressDurationInSeconds"]?.asInt()
            restStressDurationInSeconds = node["restStressDurationInSeconds"]?.asInt()
            activityStressDurationInSeconds = node["activityStressDurationInSeconds"]?.asInt()
            lowStressDurationInSeconds = node["lowStressDurationInSeconds"]?.asInt()
            mediumStressDurationInSeconds = node["mediumStressDurationInSeconds"]?.asInt()
            highStressDurationInSeconds = node["highStressDurationInSeconds"]?.asInt()
            stressQualifier = node["stressQualifier"]?.asText()
            stepsGoal = node["stepsGoal"]?.asInt()
            netKilocaloriesGoal = node["netKilocaloriesGoal"]?.asInt()
            intensityDurationGoalInSeconds = node["intensityDurationGoalInSeconds"]?.asInt()
            floorsClimbedGoal = node["floorsClimbedGoal"]?.asInt()
            source = node["source"]?.asText()
        }.build()
    }

    @Throws(BadRequestException::class)
    override fun validate(tree: JsonNode) {
        val dailies = tree[ROOT]
        if (dailies == null || !dailies.isArray) {
            throw BadRequestException("The Dailies Data was invalid")
        }
    }

    companion object {
        const val ROOT = "dailies"
    }
}
