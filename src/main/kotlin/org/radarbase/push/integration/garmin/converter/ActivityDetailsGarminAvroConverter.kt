package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminActivityDetails
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class ActivityDetailsGarminAvroConverter(topic: String = "push_integration_garmin_activity_detail") :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) {
        val activityDetails = tree[ROOT]
        if (activityDetails == null || !activityDetails.isArray) {
            throw BadRequestException("The Activity Details Data was invalid")
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

    private fun getRecord(node: JsonNode): SpecificRecord {
        val summary = node[SUB_NODE]
        return GarminActivityDetails.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = summary["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = summary["startTimeOffsetInSeconds"]?.asInt()
            activityType = summary["activityType"]?.asText()
            durationInSeconds = summary["durationInSeconds"]?.asInt()
            averageBikeCadenceInRoundsPerMinute =
                summary["averageBikeCadenceInRoundsPerMinute"]?.asDouble()
            averageHeartRateInBeatsPerMinute =
                summary["averageHeartRateInBeatsPerMinute"]?.asInt()
            averageRunCadenceInStepsPerMinute =
                summary["averageRunCadenceInStepsPerMinute"]?.asDouble()
            averageSpeedInMetersPerSecond =
                summary["averageSpeedInMetersPerSecond"]?.asDouble()
            averageSwimCadenceInStrokesPerMinute =
                summary["averageSwimCadenceInStrokesPerMinute"]?.asDouble()
            averagePaceInMinutesPerKilometer =
                summary["averagePaceInMinutesPerKilometer"]?.asDouble()
            activeKilocalories = summary["activeKilocalories"]?.asInt()
            deviceName = summary["deviceName"]?.asText()
            distanceInMeters = summary["distanceInMeters"]?.asDouble()
            maxBikeCadenceInRoundsPerMinute =
                summary["maxBikeCadenceInRoundsPerMinute"]?.asDouble()
            maxHeartRateInBeatsPerMinute =
                summary["maxHeartRateInBeatsPerMinute"]?.asInt()
            maxPaceInMinutesPerKilometer =
                summary["maxPaceInMinutesPerKilometer"]?.asDouble()
            maxRunCadenceInStepsPerMinute =
                summary["maxRunCadenceInStepsPerMinute"]?.asDouble()
            maxSpeedInMetersPerSecond = summary["maxSpeedInMetersPerSecond"]?.asDouble()
            numberOfActiveLengths = summary["numberOfActiveLengths"]?.asInt()
            startingLatitudeInDegree = summary["startingLatitudeInDegree"]?.asDouble()
            startingLongitudeInDegree = summary["startingLongitudeInDegree"]?.asDouble()
            steps = summary["steps"]?.asInt()
            totalElevationGainInMeters = summary["totalElevationGainInMeters"]?.asDouble()
            totalElevationLossInMeters = summary["totalElevationLossInMeters"]?.asDouble()
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
