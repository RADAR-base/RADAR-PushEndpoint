/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.resource

import jakarta.inject.Singleton
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.push.integration.google.auth.GoogleHealthAuthValidator.Companion.HANDSHAKE_PROPERTY
import org.radarbase.push.integration.google.auth.GoogleHealthAuthValidator.Companion.PING_PROPERTY
import org.radarbase.googlehealth.model.GoogleHealthPing
import org.radarbase.push.integration.google.service.GoogleHealthApiService
import org.slf4j.LoggerFactory

@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Path("googlehealth")
@Authenticated
class GoogleHealthPushEndpoint(
    @param:Context private val healthApiService: GoogleHealthApiService,
) {
    @POST
    @Path("notifications")
    fun onNotification(@Context request: ContainerRequestContext): Response {
        if (request.getProperty(HANDSHAKE_PROPERTY) == true) {
            return Response.ok().build()
        }

        val pings = extractPings(request.getProperty(PING_PROPERTY))
        pings.forEach { healthApiService.handlePing(it) }
        return Response.noContent().build()
    }

    private fun extractPings(raw: Any?): List<GoogleHealthPing> = when (raw) {
        null -> emptyList()
        is List<*> -> {
            val pings = raw.filterIsInstance<GoogleHealthPing>()
            if (pings.size != raw.size) {
                logger.warn(
                    "Google Health {} held {} element(s); only {} were GoogleHealthPing, ignoring the rest",
                    PING_PROPERTY, raw.size, pings.size,
                )
            }
            pings
        }
        else -> {
            logger.warn(
                "Google Health {} was a {} but List<GoogleHealthPing> was expected; skipping push",
                PING_PROPERTY, raw.javaClass.name,
            )
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleHealthPushEndpoint::class.java)
    }
}
