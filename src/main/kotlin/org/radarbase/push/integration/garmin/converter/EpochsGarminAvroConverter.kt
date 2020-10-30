package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminEpochSummary
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class EpochsGarminAvroConverter(topic: String = "push_integration_garmin_epoch") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val epochs = tree[ROOT]
        if (epochs == null || !epochs.isArray) {
            throw BadRequestException("The epochs data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminEpochSummary {
        return GarminEpochSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            steps = node["steps"]?.asInt()
            distanceInMeters = node["distanceInMeters"]?.asDouble()
            activeTimeInSeconds = node["activeTimeInSeconds"]?.asInt()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            met = node["met"]?.asDouble()
            intensity = node["intensity"]?.asText()
            meanMotionIntensity = node["meanMotionIntensity"]?.asDouble()
            maxMotionIntensity = node["maxMotionIntensity"]?.asDouble()
        }.build()
    }

    companion object {
        const val ROOT = "epochs"
    }
}
