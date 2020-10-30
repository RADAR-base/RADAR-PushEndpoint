package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminPulseOx
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class PulseOxGarminAvroConverter(topic: String = "push_integration_garmin_pulse_ox") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val pulseOx = tree[ROOT]
        if (pulseOx == null || !pulseOx.isArray) {
            throw BadRequestException("The Pulse Ox data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminPulseOx {
        return GarminPulseOx.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            calendarDate = node["calendarDate"]?.asText()
            timeOffsetSpo2Values = getMap(node["timeOffsetSpo2Values"]) ?: emptyMap()
            onDemand = node["onDemand"]?.asBoolean()
        }.build()
    }

    companion object {
        const val ROOT = "pulseOx"
    }
}
