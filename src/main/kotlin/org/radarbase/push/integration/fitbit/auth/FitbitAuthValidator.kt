package org.radarbase.push.integration.fitbit.auth

import jakarta.ws.rs.container.ContainerRequestContext
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator

class FitbitAuthValidator : AuthValidator {
    override fun verify(token: String, request: ContainerRequestContext): Auth? {
        TODO("Not yet implemented")
    }
}