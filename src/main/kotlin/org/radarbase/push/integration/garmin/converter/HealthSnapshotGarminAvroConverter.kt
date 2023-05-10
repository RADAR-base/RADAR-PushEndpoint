package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.BadRequestException
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminHealthSnapshotSummary
import java.time.Instant

class HealthSnapshotGarminAvroConverter(topic: String = "push_integration_garmin_health_snapshot") :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) {
        val activities = tree[ROOT]
        if (activities == null || !activities.isArray) {
            throw BadRequestException("The manual activities data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): SpecificRecord {
        return GarminHealthSnapshotSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            date = node["calendarDate"]?.asText()
            duration = node["durationInSeconds"]?.asInt()
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()

            for (summary in node["summaries"]) {
                when (summary["summaryType"]?.asText()) {
                    "heart_rate" -> {
                        heartRateAverage = summary["avgValue"]?.floatValue()
                        heartRateMax = summary["maxValue"]?.floatValue()
                        heartRateMin = summary["minValue"]?.floatValue()
                    }

                    "respiration" -> {
                        respirationAverage = summary["avgValue"]?.floatValue()
                        respirationMax = summary["maxValue"]?.floatValue()
                        respirationMin = summary["minValue"]?.floatValue()
                    }

                    "stress" -> {
                        stressAverage = summary["avgValue"]?.floatValue()
                        stressMax = summary["maxValue"]?.floatValue()
                        stressMin = summary["minValue"]?.floatValue()
                    }

                    "spo2" -> {
                        spo2Average = summary["avgValue"]?.floatValue()
                        spo2Max = summary["maxValue"]?.floatValue()
                        spo2Min = summary["minValue"]?.floatValue()
                    }

                    "rmssd_hrv" -> rmssdHrvAverage = summary["avgValue"]?.floatValue()

                    "sdrr_hrv" -> sdrrHrvAverage = summary["avgValue"]?.floatValue()
                }
            }
        }.build()
    }

    companion object {
        const val ROOT = "healthSnapshot"
    }
}
