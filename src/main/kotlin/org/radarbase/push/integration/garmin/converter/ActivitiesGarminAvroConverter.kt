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
        val activities = tree["activities"]
        if (activities == null || !activities.isArray) {
            throw BadRequestException("The activities data was invalid.")
        }
    }

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        val observationKey = observationKey(request)
        return tree["activities"]
            .map { node -> Pair(observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminActivitySummary {
        return GarminActivitySummary.newBuilder().apply {
            summaryId = node.get("summaryId")?.asText()
            time = node.get("startTimeInSeconds").asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node.get("startTimeOffsetInSeconds")?.asInt()
            activityType = node.get("activityType")?.asText()
            durationInSeconds = node.get("durationInSeconds")?.asInt()
            averageBikeCadenceInRoundsPerMinute =
                node.get("averageBikeCadenceInRoundsPerMinute")?.asDouble()
            averageHeartRateInBeatsPerMinute =
                node.get("averageHeartRateInBeatsPerMinute")?.asInt()
            averageRunCadenceInStepsPerMinute =
                node.get("averageRunCadenceInStepsPerMinute")?.asDouble()
            averageSpeedInMetersPerSecond =
                node.get("averageSpeedInMetersPerSecond")?.asDouble()
            averageSwimCadenceInStrokesPerMinute =
                node.get("averageSwimCadenceInStrokesPerMinute")?.asDouble()
            averagePaceInMinutesPerKilometer =
                node.get("averagePaceInMinutesPerKilometer")?.asDouble()
            activeKilocalories = node.get("activeKilocalories")?.asInt()
            deviceName = node.get("deviceName")?.asText()
            distanceInMeters = node.get("distanceInMeters")?.asDouble()
            maxBikeCadenceInRoundsPerMinute =
                node.get("maxBikeCadenceInRoundsPerMinute")?.asDouble()
            maxHeartRateInBeatsPerMinute =
                node.get("maxHeartRateInBeatsPerMinute")?.asInt()
            maxPaceInMinutesPerKilometer =
                node.get("maxPaceInMinutesPerKilometer")?.asDouble()
            maxRunCadenceInStepsPerMinute =
                node.get("maxRunCadenceInStepsPerMinute")?.asDouble()
            maxSpeedInMetersPerSecond = node.get("maxSpeedInMetersPerSecond")?.asDouble()
            numberOfActiveLengths = node.get("numberOfActiveLengths")?.asInt()
            startingLatitudeInDegree = node.get("startingLatitudeInDegree")?.asDouble()
            startingLongitudeInDegree = node.get("startingLongitudeInDegree")?.asDouble()
            steps = node.get("steps")?.asInt()
            totalElevationGainInMeters = node.get("totalElevationGainInMeters")?.asDouble()
            totalElevationLossInMeters = node.get("totalElevationLossInMeters")?.asDouble()
            isParent = node.get("isParent")?.asBoolean()
            parentSummaryId = node.get("parentSummaryId")?.asText()
            manual = node.get("manual")?.asBoolean()
        }.build()
    }
}
