package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminPulseOx
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class PulseOxGarminAvroConverter(topic: String = "push_integration_garmin_pulse_ox") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val pulseOx = tree[ROOT]
        if (pulseOx == null || !pulseOx.isArray) {
            throw BadRequestException("The Pulse Ox data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {

        return tree[ROOT]
            .map { node ->
                getRecords(node, user.observationKey)
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
                    startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
                    duration = node["durationInSeconds"]?.asInt()
                    date = node["calendarDate"]?.asText()
                    spo2Value = value?.floatValue()
                    onDemand = node["onDemand"]?.asBoolean()
                }.build()
            )
        }.toList()

    }

    companion object {
        const val ROOT = "pulseox"
        const val SUB_NODE = "timeOffsetSpo2Values"
    }
}
