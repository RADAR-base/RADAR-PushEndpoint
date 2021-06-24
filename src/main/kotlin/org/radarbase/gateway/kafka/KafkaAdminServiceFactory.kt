package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config

class KafkaAdminServiceFactory(@Context private val config: Config): DisposableSupplier<KafkaAdminService> {
    override fun get() = KafkaAdminService(config)

    override fun dispose(instance: KafkaAdminService?) {
        instance?.close()
    }
}
