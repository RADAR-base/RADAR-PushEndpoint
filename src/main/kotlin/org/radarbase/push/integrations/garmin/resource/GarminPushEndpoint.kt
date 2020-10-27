package org.radarbase.push.integrations.garmin.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.push.integrations.garmin.service.GarminHealthApiService
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
    fun addDalies(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got Dailies Data: {}", tree.toPrettyString())
        return healthApiService.processDailies(tree, requestContext)
    }

    @POST
    @Path("activities")
    fun addActivities(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        logger.info("Got Activities Data: {}", tree.toPrettyString())
        return healthApiService.processActivities(tree, requestContext)
    }

    @POST
    @Path("activityDetails")
    fun addActivityDetails(
        tree: JsonNode,
        @Context requestContext: ContainerRequestContext
    ): Response {
        // todo remove
        logger.info("Got Activity Details Data: {}", tree.toPrettyString())
        return healthApiService.processActivityDetails(tree, requestContext)
    }

    @POST
    @Path("manualActivities")
    fun addManualActivities(
        tree: JsonNode,
        @Context requestContext: ContainerRequestContext
    ): Response {
/*        if (pushBody.getManuallyUpdatedActivities() == null
            || pushBody.getManuallyUpdatedActivities().stream().anyMatch { act -> !act.getManual() }
        ) {
            logger.warn("Manual Activity data was invalid. Returning 400")
            return Response.status(Response.Status.BAD_REQUEST).build()
        }*/
        // todo remove
        logger.info(
            "Got Manual Activity Data: {}", tree.toPrettyString()
        )
        return healthApiService.processManualActivities(tree, requestContext)
    }

    @POST
    @Path("epochSummaries")
    fun addEpochSummaries(
        tree: JsonNode,
        @Context requestContext: ContainerRequestContext
    ): Response {
        // todo remove
        logger.info("Got Epoch Data: {}", tree.toPrettyString())
        return healthApiService.processEpochs(tree, requestContext)
    }

    @POST
    @Path("sleeps")
    fun addSleeps(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got Sleep Data: {}", tree.toPrettyString())
        return healthApiService.processSleeps(tree, requestContext)
    }

    @POST
    @Path("bodyCompositions")
    fun addBodyCompositions(
        tree: JsonNode,
        @Context requestContext: ContainerRequestContext
    ): Response {
        // todo remove
        logger.info("Got Body Comp Data: {}", tree.toPrettyString())
        return healthApiService.processBodyCompositions(tree, requestContext)
    }

    @POST
    @Path("stress")
    fun addStress(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got Stress Data: {}", tree.toPrettyString())
        return healthApiService.processStress(tree, requestContext)
    }

    @POST
    @Path("userMetrics")
    fun addUserMetrics(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got User Metrics Data: {}", tree.toPrettyString())
        return healthApiService.processUserMetrics(tree, requestContext)
    }

    @POST
    @Path("moveIQ")
    fun addMoveIQ(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got Move IQ Data: {}", tree.toPrettyString())
        return healthApiService.processMoveIQ(tree, requestContext)
    }

    @POST
    @Path("pulseOX")
    fun addPluseOX(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        // todo remove
        logger.info("Got Pulse OX Data: {}", tree.toPrettyString())
        return healthApiService.processPulseOx(tree, requestContext)
    }

    @POST
    @Path("respiration")
    fun addRespiration(
        tree: JsonNode,
        @Context requestContext: ContainerRequestContext
    ): Response {
        // todo remove
        logger.info("Got Respiration Data: {}", tree.toPrettyString())
        return healthApiService.processRespiration(tree, requestContext)
    }

    @POST
    @Path("deregister")
    fun deregisterUser(tree: JsonNode, @Context requestContext: ContainerRequestContext): Response {
        logger.info("Deregistering user: {}", tree.get("userId"))
        return healthApiService.handleDeregistration(
            tree.get("userId").asText(),
            tree.get("userAccessToken").asText()
        )
    }


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GarminPushEndpoint::class.java)
    }

}
