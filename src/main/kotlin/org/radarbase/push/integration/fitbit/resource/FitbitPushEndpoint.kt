package org.radarbase.push.integration.fitbit.resource

import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Named
import jakarta.inject.Singleton
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.jersey.exception.HttpInternalServerException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.filter.ClientDomainVerification
import org.radarbase.push.integration.fitbit.service.FitbitApiService

@Singleton
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/fitbit")
class FitbitPushEndpoint(
    @Context private val fitbitApiService : FitbitApiService,
    @Context @Named(DelegatedAuthValidator.FITBIT_QUALIFIER) private val userTreeMap: MutableMap<User, JsonNode>,
) {
    @GET
    @Path("")
    @ClientDomainVerification("fitbit.com")
    fun verify(@QueryParam("verify") verificationCode: String): Response {
        return fitbitApiService.verifySubscriber(verificationCode)
    }

    @POST
    @Authenticated
    @ClientDomainVerification("fitbit.com")
    fun submitNotification(): Response {
        val responses = userTreeMap.map { (user, tree) ->
            fitbitApiService.addNotifications(user, tree)
        }

        if (responses.any { it.status !in 200..299 }) {
            throw HttpInternalServerException(
                "exception", "There was an exception while processing the data."
            )
        }

        return Response.noContent().build()

    }
}
