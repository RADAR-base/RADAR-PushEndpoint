package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config

class ProducerPoolFactory(
        @Context private val config: Config
): DisposableSupplier<ProducerPool> {
    override fun get(): ProducerPool = ProducerPool(config)

    override fun dispose(instance: ProducerPool?) {
        instance?.close()
    }
}
