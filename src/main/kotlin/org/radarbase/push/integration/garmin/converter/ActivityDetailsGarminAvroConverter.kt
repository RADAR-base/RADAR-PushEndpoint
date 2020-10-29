package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminActivityDetails
import org.radarcns.push.integration.garmin.GarminActivityDetailsSample
import org.radarcns.push.integration.garmin.GarminActivityDetailsSummary
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
        return GarminActivityDetails.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            summary = createSummary(node["summary"])
            samples = node["samples"]?.map { sample -> createSample(sample) } ?: listOf()
        }.build()
    }

    private fun createSummary(node: JsonNode): GarminActivityDetailsSummary {
        return GarminActivityDetailsSummary.newBuilder().apply {
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

    private fun createSample(node: JsonNode): GarminActivityDetailsSample {
        return GarminActivityDetailsSample.newBuilder().apply {
            startTimeInSeconds = node["startTimeInSeconds"].asDouble()
            airTemperatureCelcius = node["airTemperatureCelcius"]?.asDouble()
            heartrate = node["heartrate"]?.asInt()
            speedMetersPerSecond = node["speedMetersPerSecond"]?.asDouble()
            stepsPerMinute = node["stepsPerMinute"]?.asDouble()
            totalDistanceInMeters = node["totalDistanceInMeters"]?.asDouble()
            timerDurationInSeconds = node["timerDurationInSeconds"]?.asInt()
            clockDurationInSeconds = node["clockDurationInSeconds"]?.asInt()
            movingDurationInSeconds = node["movingDurationInSeconds"]?.asInt()
            powerInWatts = node["powerInWatts"]?.asDouble()
            bikeCadenceInRPM = node["bikeCadenceInRPM"]?.asInt()
            swimCadenceInStrokesPerMinute = node["swimCadenceInStrokesPerMinute"]?.asInt()
            latitudeInDegree = node["latitudeInDegree"]?.asDouble()
            longitudeInDegree = node["longitudeInDegree"]?.asDouble()
            elevationInMeters = node["elevationInMeters"]?.asDouble()
        }.build()
    }

    companion object {
        const val ROOT = "activityDetails"
    }
}
