package org.radarbase.push.integration.fitbit.service

import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status
import org.radarbase.gateway.Config
import org.radarbase.gateway.FitbitConfig
import java.io.IOException

class FitbitApiService(
    @Context private val config : Config
){
    private val fitbitConfig: FitbitConfig = config.pushIntegration.fitbit

    @Throws(IOException::class, BadRequestException::class)
    fun verifySubscriber(verificationCode : String): Response {
        if (verificationCode == fitbitConfig.verificationCode){
            return Response.noContent().build()
        }
        return Response.status(Status.NOT_FOUND).build()
    }
}