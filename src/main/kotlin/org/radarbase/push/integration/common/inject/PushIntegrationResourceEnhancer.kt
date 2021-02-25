package org.radarbase.push.integration.common.inject

import org.glassfish.jersey.internal.inject.AbstractBinder
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator

class PushIntegrationResourceEnhancer : JerseyResourceEnhancer {

    override fun AbstractBinder.enhance() {
        bind(DelegatedAuthValidator::class.java)
            .to(AuthValidator::class.java)
    }
}
