package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminBodyComposition
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class BodyCompGarminAvroConverter(topic: String = "push_integration_garmin_body_composition") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val bodyComps = tree[ROOT]
        if (bodyComps == null || !bodyComps.isArray) {
            throw BadRequestException("The Body Composition data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminBodyComposition {
        return GarminBodyComposition.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["measurementTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            measurementTimeOffsetInSeconds = node["measurementTimeOffsetInSeconds"]?.asInt()
            muscleMassInGrams = node["muscleMassInGrams"]?.asInt()
            boneMassInGrams = node["boneMassInGrams"]?.asInt()
            bodyWaterInPercent = node["bodyWaterInPercent"]?.asDouble()
            bodyFatInPercent = node["bodyFatInPercent"]?.asDouble()
            bodyMassIndex = node["bodyMassIndex"]?.asDouble()
            weightInGrams = node["weightInGrams"]?.asInt()
        }.build()
    }

    companion object {
        const val ROOT = "bodyComps"
    }
}
