package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.BadRequestException
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminBloodPressureSummary
import java.time.Instant

class BloodPressureGarminAvroConverter(topic: String = "push_integration_garmin_blood_pressure") :
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
        return GarminBloodPressureSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            measurementTimeOffset = node["measurementTimeOffsetInSeconds"]?.asInt()
            systolic = node["systolic"]?.asInt()
            diastolic = node["diastolic"]?.asInt()
            pulse = node["pulse"]?.asInt()
            sourceType = node["sourceType"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "bloodPressure"
    }
}
