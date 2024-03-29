package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminPulseOx
import java.time.Instant

class HealthSnapshotSpO2SampleGarminAvroConverter(
    topic: String = "push_integration_garmin_heart_rate_sample"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT].map { node ->
            getSamples(
                node[SUB_NODE], node["summaryId"].asText(),
                user.observationKey, node["startTimeInSeconds"].asDouble(),
                node["calendarDate"]?.asText()
            )
        }.flatten()
    }

    private fun getSamples(
        node: JsonNode?,
        summaryId: String,
        observationKey: ObservationKey,
        startTime: Double,
        date: String?
    ): List<Pair<ObservationKey, GarminPulseOx>> {
        if (node == null) {
            return emptyList()
        }

        val summary = node.find { it["summaryType"]?.asText() == "spo2" } ?: return emptyList()

        return summary["epochSummaries"].fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminPulseOx.newBuilder().apply {
                    this.summaryId = summaryId
                    this.time = startTime + key.toDouble()
                    this.timeReceived = Instant.now().toEpochMilli() / 1000.0
                    this.date = date
                    this.spo2Value = value?.floatValue()
                    this.onDemand = true
                }.build()
            )
        }.toList()
    }

    companion object {
        const val ROOT = HealthSnapshotGarminAvroConverter.ROOT
        const val SUB_NODE = "summaries"
    }
}
