package org.radarbase.push.integration.common.filter

import java.io.IOException
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

@Provider
class CorsFilter : ContainerResponseFilter {
    @Throws(IOException::class)
    override fun filter(
        requestContext: ContainerRequestContext, cres: ContainerResponseContext
    ) {
        cres.headers.add("Access-Control-Allow-Origin", "*")
        cres.headers
            .add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
        cres.headers.add("Access-Control-Allow-Credentials", "true")
        cres.headers.add("Access-Control-Allow-Methods", "POST, OPTIONS")
        cres.headers.add("Access-Control-Max-Age", "1209600")
    }
}
