package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.integration.garmin.GarminActivityDetailsSample
import java.time.Instant
import javax.ws.rs.container.ContainerRequestContext

class ActivityDetailsSampleGarminAvroConverter(
    topic: String = "push_integration_garmin_activity_detail_sample"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {
        val observationKey = observationKey(request)
        return tree[ROOT]
            .map { node ->
                createSamples(
                    node[SUB_NODE], node["summaryId"].asText(), observationKey
                )
            }.flatten()
    }

    private fun createSamples(
        samples: JsonNode?, summaryId: String, observationKey:
        ObservationKey
    ): List<Pair<ObservationKey, GarminActivityDetailsSample>> {
        if (samples == null) {
            return emptyList()
        }
        return samples.map { sample ->
            Pair(
                observationKey,
                GarminActivityDetailsSample.newBuilder().apply {
                    this.summaryId = summaryId
                    time = sample["startTimeInSeconds"].asDouble()
                    timeReceived = Instant.now().toEpochMilli() / 1000.0
                    airTemperatureCelcius = sample["airTemperatureCelcius"]?.asDouble()
                    heartrate = sample["heartRate"]?.asInt()
                    speedMetersPerSecond = sample["speedMetersPerSecond"]?.asDouble()
                    stepsPerMinute = sample["stepsPerMinute"]?.asDouble()
                    totalDistanceInMeters = sample["totalDistanceInMeters"]?.asDouble()
                    timerDurationInSeconds = sample["timerDurationInSeconds"]?.asInt()
                    clockDurationInSeconds = sample["clockDurationInSeconds"]?.asInt()
                    movingDurationInSeconds = sample["movingDurationInSeconds"]?.asInt()
                    powerInWatts = sample["powerInWatts"]?.asDouble()
                    bikeCadenceInRPM = sample["bikeCadenceInRPM"]?.asInt()
                    swimCadenceInStrokesPerMinute = sample["swimCadenceInStrokesPerMinute"]?.asInt()
                    latitudeInDegree = sample["latitudeInDegree"]?.asDouble()
                    longitudeInDegree = sample["longitudeInDegree"]?.asDouble()
                    elevationInMeters = sample["elevationInMeters"]?.asDouble()
                }.build()
            )
        }

    }

    companion object {
        const val ROOT = "activityDetails"
        const val SUB_NODE = "samples"
    }
}
