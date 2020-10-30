package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminRespiration
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class RespirationGarminAvroConverter(topic: String = "push_integration_garmin_respiration") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val respiration = tree[ROOT]
        if (respiration == null || !respiration.isArray) {
            throw BadRequestException("The Respiration data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminRespiration {
        return GarminRespiration.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            timeOffsetEpochToBreaths = getMap(node["timeOffsetEpochToBreaths"]) ?: emptyMap()
            durationInSeconds = node["durationInSeconds"]?.asInt()
        }.build()
    }

    companion object {
        const val ROOT = "respiration"
    }
}
