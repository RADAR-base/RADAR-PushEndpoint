package org.radarbase.push.integration.garmin.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.push.integration.garmin.service.GarminHealthApiService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Singleton
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.container.ContainerRequestContext
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
    fun addDalies(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processDailies(tree, requestContext)
    }

    @POST
    @Path("activities")
    fun addActivities(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processActivities(tree, requestContext)
    }

    @POST
    @Path("activityDetails")
    fun addActivityDetails(
        @Context requestContext: ContainerRequestContext
    ): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processActivityDetails(tree, requestContext)
    }

    @POST
    @Path("manualActivities")
    fun addManualActivities(
        @Context requestContext: ContainerRequestContext
    ): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processManualActivities(tree, requestContext)
    }

    @POST
    @Path("epochs")
    fun addEpochSummaries(
        @Context requestContext: ContainerRequestContext
    ): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processEpochs(tree, requestContext)
    }

    @POST
    @Path("sleeps")
    fun addSleeps(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processSleeps(tree, requestContext)
    }

    @POST
    @Path("bodyCompositions")
    fun addBodyCompositions(
        @Context requestContext: ContainerRequestContext
    ): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processBodyCompositions(tree, requestContext)
    }

    @POST
    @Path("stress")
    fun addStress(@Context requestContext: ContainerRequestContext): Response {
        // todo remove
        val tree = requestContext.getProperty("tree") as JsonNode
        logger.info("Got Stress Data: {}", tree.toPrettyString())
        return healthApiService.processStress(tree, requestContext)
    }

    @POST
    @Path("userMetrics")
    fun addUserMetrics(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processUserMetrics(tree, requestContext)
    }

    @POST
    @Path("moveIQ")
    fun addMoveIQ(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processMoveIQ(tree, requestContext)
    }

    @POST
    @Path("pulseOx")
    fun addPluseOX(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processPulseOx(tree, requestContext)
    }

    @POST
    @Path("respiration")
    fun addRespiration(
        @Context requestContext: ContainerRequestContext
    ): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        return healthApiService.processRespiration(tree, requestContext)
    }

    @POST
    @Path("deregister")
    fun deregisterUser(@Context requestContext: ContainerRequestContext): Response {
        val tree = requestContext.getProperty("tree") as JsonNode
        logger.info("Deregistering user: {}", tree.get("userId"))
        return healthApiService.handleDeregistration(
            tree.get("userId")?.asText()
        )
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GarminPushEndpoint::class.java)
    }

}
