package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminActivitySummary
import java.time.Instant
import jakarta.ws.rs.BadRequestException

open class ActivitiesGarminAvroConverter(topic: String = "push_integration_garmin_activity") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val activities = tree[ROOT]
        if (activities == null || !activities.isArray) {
            throw BadRequestException("The activities data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    protected fun getRecord(node: JsonNode): GarminActivitySummary {
        return GarminActivitySummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            duration = node["durationInSeconds"]?.asInt()
            averageBikeCadence = node["averageBikeCadenceInRoundsPerMinute"]?.floatValue()
            averageHeartRate = node["averageHeartRateInBeatsPerMinute"]?.asInt()
            averageRunCadence = node["averageRunCadenceInStepsPerMinute"]?.floatValue()
            averageSpeed = node["averageSpeedInMetersPerSecond"]?.floatValue()
            averageSwimCadence = node["averageSwimCadenceInStrokesPerMinute"]?.floatValue()
            averagePace = node["averagePaceInMinutesPerKilometer"]?.floatValue()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            deviceName = node["deviceName"]?.asText()
            distance = node["distanceInMeters"]?.floatValue()
            maxBikeCadence = node["maxBikeCadenceInRoundsPerMinute"]?.floatValue()
            maxHeartRate = node["maxHeartRateInBeatsPerMinute"]?.asInt()
            maxPace = node["maxPaceInMinutesPerKilometer"]?.floatValue()
            maxRunCadence = node["maxRunCadenceInStepsPerMinute"]?.floatValue()
            maxSpeed = node["maxSpeedInMetersPerSecond"]?.floatValue()
            numberOfActiveLengths = node["numberOfActiveLengths"]?.asInt()
            startingLatitude = node["startingLatitudeInDegree"]?.floatValue()
            startingLongitude = node["startingLongitudeInDegree"]?.floatValue()
            steps = node.get("steps")?.asInt()
            totalElevationGain = node["totalElevationGainInMeters"]?.floatValue()
            totalElevationLoss = node["totalElevationLossInMeters"]?.floatValue()
            isParent = node["isParent"]?.asBoolean()
            parentSummaryId = node["parentSummaryId"]?.asText()
            manual = node["manual"]?.asBoolean()
        }.build()
    }

    companion object {
        const val ROOT = "activities"
    }
}
