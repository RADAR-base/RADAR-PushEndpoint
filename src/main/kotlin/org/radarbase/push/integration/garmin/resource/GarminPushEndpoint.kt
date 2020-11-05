package org.radarbase.push.integration.garmin.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GARMIN_QUALIFIER
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.garmin.service.GarminHealthApiService
import javax.inject.Named
import javax.inject.Singleton
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Path("garmin")
@Authenticated
class GarminPushEndpoint(
    @Context private val healthApiService: GarminHealthApiService,
    @Context @Named(GARMIN_QUALIFIER) private val tree: JsonNode,
    @Context @Named(GARMIN_QUALIFIER) private val user: User
) {

    @POST
    @Path("dailies")
    fun addDalies(): Response {
        return healthApiService.processDailies(tree, user)
    }

    @POST
    @Path("activities")
    fun addActivities(): Response {
        return healthApiService.processActivities(tree, user)
    }

    @POST
    @Path("activityDetails")
    fun addActivityDetails(): Response {
        return healthApiService.processActivityDetails(tree, user)
    }

    @POST
    @Path("manualActivities")
    fun addManualActivities(): Response {
        return healthApiService.processManualActivities(tree, user)
    }

    @POST
    @Path("epochs")
    fun addEpochSummaries(): Response {
        return healthApiService.processEpochs(tree, user)
    }

    @POST
    @Path("sleeps")
    fun addSleeps(): Response {
        return healthApiService.processSleeps(tree, user)
    }

    @POST
    @Path("bodyCompositions")
    fun addBodyCompositions(): Response {
        return healthApiService.processBodyCompositions(tree, user)
    }

    @POST
    @Path("stress")
    fun addStress(): Response {
        return healthApiService.processStress(tree, user)
    }

    @POST
    @Path("userMetrics")
    fun addUserMetrics(): Response {
        return healthApiService.processUserMetrics(tree, user)
    }

    @POST
    @Path("moveIQ")
    fun addMoveIQ(): Response {
        return healthApiService.processMoveIQ(tree, user)
    }

    @POST
    @Path("pulseOx")
    fun addPluseOX(): Response {
        return healthApiService.processPulseOx(tree, user)
    }

    @POST
    @Path("respiration")
    fun addRespiration(): Response {
        return healthApiService.processRespiration(tree, user)
    }

    @POST
    @Path("deregister")
    fun deregisterUser(): Response {
        return healthApiService.handleDeregistration(user)
    }

}
