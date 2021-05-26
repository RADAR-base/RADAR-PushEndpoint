package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminActivityDetails
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class ActivityDetailsGarminAvroConverter(topic: String = "push_integration_garmin_activity_detail") :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) {
        val activityDetails = tree[ROOT]
        if (activityDetails == null || !activityDetails.isArray) {
            throw BadRequestException("The Activity Details Data was invalid")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): SpecificRecord {
        val summary = node[SUB_NODE]
        return GarminActivityDetails.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = summary["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffset = summary["startTimeOffsetInSeconds"]?.asInt()
            activityType = summary["activityType"]?.asText()
            duration = summary["durationInSeconds"]?.asInt()
            averageBikeCadence = summary["averageBikeCadenceInRoundsPerMinute"]?.floatValue()
            averageHeartRate = summary["averageHeartRateInBeatsPerMinute"]?.asInt()
            averageRunCadence = summary["averageRunCadenceInStepsPerMinute"]?.floatValue()
            averageSpeed = summary["averageSpeedInMetersPerSecond"]?.floatValue()
            averageSwimCadence = summary["averageSwimCadenceInStrokesPerMinute"]?.floatValue()
            averagePace = summary["averagePaceInMinutesPerKilometer"]?.floatValue()
            activeKilocalories = summary["activeKilocalories"]?.asInt()
            deviceName = summary["deviceName"]?.asText()
            distance = summary["distanceInMeters"]?.floatValue()
            maxBikeCadence = summary["maxBikeCadenceInRoundsPerMinute"]?.floatValue()
            maxHeartRate = summary["maxHeartRateInBeatsPerMinute"]?.asInt()
            maxPace = summary["maxPaceInMinutesPerKilometer"]?.floatValue()
            maxRunCadence = summary["maxRunCadenceInStepsPerMinute"]?.floatValue()
            maxSpeed = summary["maxSpeedInMetersPerSecond"]?.floatValue()
            numberOfActiveLengths = summary["numberOfActiveLengths"]?.asInt()
            startingLatitude = summary["startingLatitudeInDegree"]?.floatValue()
            startingLongitude = summary["startingLongitudeInDegree"]?.floatValue()
            steps = summary["steps"]?.asInt()
            totalElevationGain = summary["totalElevationGainInMeters"]?.floatValue()
            totalElevationLoss = summary["totalElevationLossInMeters"]?.floatValue()
            isParent = summary["isParent"]?.asBoolean()
            parentSummaryId = summary["parentSummaryId"]?.asText()
            manual = summary["manual"]?.asBoolean()
        }.build()
    }

    companion object {
        const val ROOT = "activityDetails"
        const val SUB_NODE = "summary"
    }
}
