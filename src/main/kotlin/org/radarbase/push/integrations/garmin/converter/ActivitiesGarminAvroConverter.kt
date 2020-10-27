package org.radarbase.push.integrations.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.generic.GenericRecord
import javax.ws.rs.container.ContainerRequestContext

class ActivitiesGarminAvroConverter(topic: String = "push_integration_garmin_activities") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        TODO("Not yet implemented")
    }

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<GenericRecord, GenericRecord>> {
        TODO("Not yet implemented")
    }
}
