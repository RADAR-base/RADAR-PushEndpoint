package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminStressDetailSummary
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class StressDetailsGarminAvroConverter(topic: String = "push_integration_garmin_stress") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val stress = tree[ROOT]
        if (stress == null || !stress.isArray) {
            throw BadRequestException("The Stress data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminStressDetailSummary {
        return GarminStressDetailSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            calendarDate = node["calendarDate"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "stressDetails"
    }
}
