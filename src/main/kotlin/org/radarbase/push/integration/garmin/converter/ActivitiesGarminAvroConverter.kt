package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class ActivitiesGarminAvroConverter(topic: String = "push_integration_garmin_activities") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val activities = tree["activities"]
        if (activities == null || !activities.isArray) {
            throw BadRequestException("The activities data was invalid.")
        }
    }

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        for (node in tree["activities"]) {
            // create a Pair fo each record.
        }

        return listOf(
            Pair(
                observationKey(request),
                observationKey(request)
            )
        )
    }
}
