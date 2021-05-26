package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminDailySummary
import java.io.IOException
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class DailiesGarminAvroConverter(topic: String = "push_integration_garmin_daily") :
    GarminAvroConverter(topic) {

    @Throws(IOException::class)
    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): SpecificRecord {
        return GarminDailySummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            date = node["calendarDate"]?.asText()
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            duration = node["durationInSeconds"]?.asInt()
            steps = node["steps"]?.asInt()
            distance = node["distanceInMeters"]?.floatValue()
            activeTime = node["activeTimeInSeconds"]?.asInt()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            bmrKilocalories = node["bmrKilocalories"]?.asInt()
            consumedCalories = node["consumedCalories"]?.asInt()
            moderateIntensityDuration = node["moderateIntensityDurationInSeconds"]?.asInt()
            vigorousIntensityDuration = node["vigorousIntensityDurationInSeconds"]?.asInt()
            floorsClimbed = node["floorsClimbed"]?.asInt()
            minHeartRate = node["minHeartRateInBeatsPerMinute"]?.asInt()
            averageHeartRate = node["averageHeartRateInBeatsPerMinute"]?.asInt()
            maxHeartRate = node["maxHeartRateInBeatsPerMinute"]?.asInt()
            restingHeartRate = node["restingHeartRateInBeatsPerMinute"]?.asInt()
            averageStressLevel = node["averageStressLevel"]?.asInt()
            maxStressLevel = node["maxStressLevel"]?.asInt()
            stressDuration = node["stressDurationInSeconds"]?.asInt()
            restStressDuration = node["restStressDurationInSeconds"]?.asInt()
            activityStressDuration = node["activityStressDurationInSeconds"]?.asInt()
            lowStressDuration = node["lowStressDurationInSeconds"]?.asInt()
            mediumStressDuration = node["mediumStressDurationInSeconds"]?.asInt()
            highStressDuration = node["highStressDurationInSeconds"]?.asInt()
            stressQualifier = node["stressQualifier"]?.asText()
            stepsGoal = node["stepsGoal"]?.asInt()
            netKilocaloriesGoal = node["netKilocaloriesGoal"]?.asInt()
            intensityDurationGoal = node["intensityDurationGoalInSeconds"]?.asInt()
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
