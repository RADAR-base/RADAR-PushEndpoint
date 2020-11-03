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
class GarminPushEndpoint(@Context private val healthApiService: GarminHealthApiService) {

    @POST
    @Path("dailies")
    fun addDalies(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processDailies(tree, user)
    }

    @POST
    @Path("activities")
    fun addActivities(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processActivities(tree, user)
    }

    @POST
    @Path("activityDetails")
    fun addActivityDetails(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processActivityDetails(tree, user)
    }

    @POST
    @Path("manualActivities")
    fun addManualActivities(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processManualActivities(tree, user)
    }

    @POST
    @Path("epochs")
    fun addEpochSummaries(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processEpochs(tree, user)
    }

    @POST
    @Path("sleeps")
    fun addSleeps(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processSleeps(tree, user)
    }

    @POST
    @Path("bodyCompositions")
    fun addBodyCompositions(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processBodyCompositions(tree, user)
    }

    @POST
    @Path("stress")
    fun addStress(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processStress(tree, user)
    }

    @POST
    @Path("userMetrics")
    fun addUserMetrics(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processUserMetrics(tree, user)
    }

    @POST
    @Path("moveIQ")
    fun addMoveIQ(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processMoveIQ(tree, user)
    }

    @POST
    @Path("pulseOx")
    fun addPluseOX(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processPulseOx(tree, user)
    }

    @POST
    @Path("respiration")
    fun addRespiration(
        @Context @Named(GARMIN_QUALIFIER) tree: JsonNode,
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.processRespiration(tree, user)
    }

    @POST
    @Path("deregister")
    fun deregisterUser(
        @Context @Named(GARMIN_QUALIFIER) user: User
    ): Response {
        return healthApiService.handleDeregistration(user)
    }

}
