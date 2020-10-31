package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.kafka.ObservationKey
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
            .map { node ->
                getRecords(node, observationKey)
            }.flatten()
    }

    private fun getRecords(node: JsonNode, observationKey: ObservationKey):
            List<Pair<ObservationKey, GarminPulseOx>> {
        val startTime = node["startTimeInSeconds"].asDouble()
        return node[SUB_NODE].fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminPulseOx.newBuilder().apply {
                    summaryId = node["summaryId"]?.asText()
                    time = startTime + key.toDouble()
                    timeReceived = Instant.now().toEpochMilli() / 1000.0
                    startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
                    durationInSeconds = node["durationInSeconds"]?.asInt()
                    calendarDate = node["calendarDate"]?.asText()
                    spo2Value = value?.asDouble()
                    onDemand = node["onDemand"]?.asBoolean()
                }.build()
            )
        }.toList()

    }

    companion object {
        const val ROOT = "pulseOx"
        const val SUB_NODE = "timeOffsetSpo2Values"
    }
}
