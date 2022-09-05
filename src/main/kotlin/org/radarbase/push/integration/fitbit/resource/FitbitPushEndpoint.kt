package org.radarbase.push.integration.fitbit.resource

import jakarta.inject.Singleton
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.radarbase.push.integration.fitbit.service.FitbitApiService

@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Path("/fitbit")
class FitbitPushEndpoint(
    @Context private val fitbitApiService : FitbitApiService
) {
    @GET
    @Path("")
    fun verify(@QueryParam("verify") verificationCode: String): Response {
        return fitbitApiService.verifySubscriber(verificationCode)
    }
}