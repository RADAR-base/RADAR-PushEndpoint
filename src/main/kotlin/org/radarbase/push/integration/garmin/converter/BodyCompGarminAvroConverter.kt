package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminBodyComposition
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class BodyCompGarminAvroConverter(topic: String = "push_integration_garmin_body_composition") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val bodyComps = tree[ROOT]
        if (bodyComps == null || !bodyComps.isArray) {
            throw BadRequestException("The Body Composition data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {

        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminBodyComposition {
        return GarminBodyComposition.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["measurementTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            measurementTimeOffset = node["measurementTimeOffsetInSeconds"]?.asInt()
            muscleMass = node["muscleMassInGrams"]?.asInt()
            boneMass = node["boneMassInGrams"]?.asInt()
            bodyWater = node["bodyWaterInPercent"]?.floatValue()
            bodyFat = node["bodyFatInPercent"]?.floatValue()
            bodyMassIndex = node["bodyMassIndex"]?.floatValue()
            weight = node["weightInGrams"]?.asInt()
        }.build()
    }

    companion object {
        const val ROOT = "bodyComps"
    }
}
