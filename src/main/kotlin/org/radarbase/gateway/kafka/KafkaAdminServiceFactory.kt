package org.radarbase.gateway.kafka

import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config
import jakarta.ws.rs.core.Context

class KafkaAdminServiceFactory(@Context private val config: Config): DisposableSupplier<KafkaAdminService> {
    override fun get() = KafkaAdminService(config)

    override fun dispose(instance: KafkaAdminService?) {
        instance?.close()
    }
}
