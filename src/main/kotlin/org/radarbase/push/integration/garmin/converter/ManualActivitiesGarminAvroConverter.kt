package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.BadRequestException
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User

class ManualActivitiesGarminAvroConverter(topic: String) : ActivitiesGarminAvroConverter(topic) {
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

    companion object {
        const val ROOT = "manuallyUpdatedActivities"
    }
}
