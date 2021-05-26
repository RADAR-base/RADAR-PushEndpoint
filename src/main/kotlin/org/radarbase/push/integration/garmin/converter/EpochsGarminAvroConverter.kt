package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminEpochSummary
import java.time.Instant
import jakarta.ws.rs.BadRequestException

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
        user: User
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminEpochSummary {
        return GarminEpochSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            duration = node["durationInSeconds"]?.asInt()
            steps = node["steps"]?.asInt()
            distance = node["distanceInMeters"]?.floatValue()
            activeTime = node["activeTimeInSeconds"]?.asInt()
            activeKilocalories = node["activeKilocalories"]?.asInt()
            metabolicEquivalentOfTask = node["met"]?.floatValue()
            intensity = node["intensity"]?.asText()
            meanMotionIntensity = node["meanMotionIntensity"]?.floatValue()
            maxMotionIntensity = node["maxMotionIntensity"]?.floatValue()
        }.build()
    }

    companion object {
        const val ROOT = "epochs"
    }
}
