package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminActivitySummary
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class ActivitiesGarminAvroConverter(topic: String = "push_integration_garmin_activities") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val activities = tree[ROOT]
        if (activities == null || !activities.isArray) {
            throw BadRequestException("The activities data was invalid.")
        }
    }

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        val observationKey = observationKey(request)
        return tree[ROOT]
            .map { node -> Pair(observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminActivitySummary {
        return GarminActivitySummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            averageBikeCadenceInRoundsPerMinute =
                node["averageBikeCadenceInRoundsPerMinute"]?.asDouble()
            averageHeartRateInBeatsPerMinute =
                node["averageHeartRateInBeatsPerMinute"]?.asInt()
            averageRunCadenceInStepsPerMinute =
                node["averageRunCadenceInStepsPerMinute"]?.asDouble()
            averageSpeedInMetersPerSecond =
                node["averageSpeedInMetersPerSecond"]?.asDouble()
            averageSwimCadenceInStrokesPerMinute =
                node["averageSwimCadenceInStrokesPerMinute"]?.asDouble()
            averagePaceInMinutesPerKilometer =
                node["averagePaceInMinutesPerKilometer"]?.asDouble()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            deviceName = node["deviceName"]?.asText()
            distanceInMeters = node["distanceInMeters"]?.asDouble()
            maxBikeCadenceInRoundsPerMinute =
                node["maxBikeCadenceInRoundsPerMinute"]?.asDouble()
            maxHeartRateInBeatsPerMinute =
                node["maxHeartRateInBeatsPerMinute"]?.asInt()
            maxPaceInMinutesPerKilometer =
                node["maxPaceInMinutesPerKilometer"]?.asDouble()
            maxRunCadenceInStepsPerMinute =
                node["maxRunCadenceInStepsPerMinute"]?.asDouble()
            maxSpeedInMetersPerSecond = node["maxSpeedInMetersPerSecond"]?.asDouble()
            numberOfActiveLengths = node["numberOfActiveLengths"]?.asInt()
            startingLatitudeInDegree = node["startingLatitudeInDegree"]?.asDouble()
            startingLongitudeInDegree = node["startingLongitudeInDegree"]?.asDouble()
            steps = node.get("steps")?.asInt()
            totalElevationGainInMeters = node["totalElevationGainInMeters"]?.asDouble()
            totalElevationLossInMeters = node["totalElevationLossInMeters"]?.asDouble()
            isParent = node["isParent"]?.asBoolean()
            parentSummaryId = node["parentSummaryId"]?.asText()
            manual = node["manual"]?.asBoolean()
        }.build()
    }

    companion object {
        const val ROOT = "activities"
    }
}
