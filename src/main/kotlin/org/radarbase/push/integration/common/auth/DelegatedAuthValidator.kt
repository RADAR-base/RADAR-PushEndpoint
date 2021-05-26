package org.radarbase.push.integration.common.auth

import org.glassfish.hk2.api.IterableProvider
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.auth.AuthValidator
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo

class DelegatedAuthValidator(
    @Context private val uriInfo: UriInfo,
    @Context private val namedValidators: IterableProvider<AuthValidator>
) : AuthValidator {

    private val basePath: String = "push"

    fun delegate(): AuthValidator {
        return when {
            uriInfo.matches(GARMIN_QUALIFIER) -> namedValidators.named(GARMIN_QUALIFIER).get()
            // Add support for more as integrations are added
            else -> throw IllegalStateException()
        }
    }

    private fun UriInfo.matches(name: String): Boolean =
        this.absolutePath.path.contains("^/$basePath/integrations/$name/.*".toRegex())

    companion object {
        const val GARMIN_QUALIFIER = "garmin"
    }

    override fun verify(token: String, request: ContainerRequestContext): Auth? =
        delegate().verify(token, request)

    override fun getToken(request: ContainerRequestContext): String? = delegate().getToken(request)
}
