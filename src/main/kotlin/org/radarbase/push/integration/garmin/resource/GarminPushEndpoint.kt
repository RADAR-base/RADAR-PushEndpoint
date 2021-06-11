package org.radarbase.push.integration.garmin.resource

import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.jersey.exception.HttpInternalServerException
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.service.GarminHealthApiService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Path("garmin")
@Authenticated
class GarminPushEndpoint(
    @Context private val healthApiService: GarminHealthApiService,
    // Using MutableMap due to https://discuss.kotlinlang.org/t/warning-from-jersey-due-to-signature-change/1328
    @Context @Named(GARMIN_QUALIFIER) private val userTreeMap: MutableMap<User, JsonNode>,
    @Context @Named(GARMIN_QUALIFIER) private var authMetadata: MutableMap<String, String>
) {

    @POST
    @Path("dailies")
    fun addDalies(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processDailies(tree, user)
        }
    }

    @POST
    @Path("activities")
    fun addActivities(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processActivities(tree, user)
        }
    }

    @POST
    @Path("activityDetails")
    fun addActivityDetails(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processActivityDetails(tree, user)
        }
    }

    @POST
    @Path("manualActivities")
    fun addManualActivities(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processManualActivities(tree, user)
        }
    }

    @POST
    @Path("epochs")
    fun addEpochSummaries(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processEpochs(tree, user)
        }
    }

    @POST
    @Path("sleeps")
    fun addSleeps(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processSleeps(tree, user)
        }
    }

    @POST
    @Path("bodyCompositions")
    fun addBodyCompositions(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processBodyCompositions(tree, user)
        }
    }

    @POST
    @Path("stress")
    fun addStress(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processStress(tree, user)
        }
    }

    @POST
    @Path("userMetrics")
    fun addUserMetrics(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processUserMetrics(tree, user)
        }
    }

    @POST
    @Path("moveIQ")
    fun addMoveIQ(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processMoveIQ(tree, user)
        }
    }

    @POST
    @Path("pulseOx")
    fun addPluseOX(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processPulseOx(tree, user)
        }
    }

    @POST
    @Path("respiration")
    fun addRespiration(): Response {
        return processResponses { tree: JsonNode, user: User ->
            healthApiService.processRespiration(tree, user)
        }
    }

    /**
     * Processes responses for all users
     * @param function: The function to use to process data
     */
    private fun processResponses(function: (JsonNode, User) -> Response): Response {
        val responses = userTreeMap.map { (user, tree) ->
            function(tree, user)
        }
        if (
            authMetadata.getOrDefault("isAnyUnauthorised", "false").toBoolean()
        ) {
            throw HttpUnauthorizedException(
                "invalid_auth", "One of the users did not have " +
                    "correct authorisation information."
            )
        }
        if (responses.any { it.status !in 200..299 }) {
            throw HttpInternalServerException(
                "exception", "There was an exception while processing the data."
            )
        }
        return Response.ok().build()
    }
}
