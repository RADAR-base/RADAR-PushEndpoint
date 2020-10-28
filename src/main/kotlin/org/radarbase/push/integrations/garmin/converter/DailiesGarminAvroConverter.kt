package org.radarbase.push.integrations.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import java.io.IOException
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class DailiesGarminAvroConverter(topic: String = "push_integration_garmin_dailies") :
    GarminAvroConverter(topic) {

    @Throws(IOException::class)
    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {
        TODO("Not yet implemented")
    }

    @Throws(BadRequestException::class)
    override fun validate(tree: JsonNode) {
        TODO("Not yet implemented")
    }
}
