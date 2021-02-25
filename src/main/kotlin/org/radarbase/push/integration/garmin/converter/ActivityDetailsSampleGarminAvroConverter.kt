package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminActivityDetailsSample
import java.time.Instant

class ActivityDetailsSampleGarminAvroConverter(
    topic: String = "push_integration_garmin_activity_detail_sample"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node ->
                createSamples(
                    node[SUB_NODE], node["summaryId"].asText(), user.observationKey
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
                    airTemperature = sample["airTemperatureCelcius"]?.floatValue()
                    heartRate = sample["heartRate"]?.asInt()
                    speed = sample["speedMetersPerSecond"]?.floatValue()
                    stepsPerMinute = sample["stepsPerMinute"]?.floatValue()
                    totalDistance = sample["totalDistanceInMeters"]?.floatValue()
                    timerDuration = sample["timerDurationInSeconds"]?.asInt()
                    clockDuration = sample["clockDurationInSeconds"]?.asInt()
                    movingDuration = sample["movingDurationInSeconds"]?.asInt()
                    power = sample["powerInWatts"]?.floatValue()
                    bikeCadence = sample["bikeCadenceInRPM"]?.asInt()
                    swimCadence = sample["swimCadenceInStrokesPerMinute"]?.asInt()
                    latitude = sample["latitudeInDegree"]?.floatValue()
                    longitude = sample["longitudeInDegree"]?.floatValue()
                    elevation = sample["elevationInMeters"]?.floatValue()
                }.build()
            )
        }

    }

    companion object {
        const val ROOT = "activityDetails"
        const val SUB_NODE = "samples"
    }
}
