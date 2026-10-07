package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.radarbase.jersey.service.HealthService
import java.util.concurrent.Executors

class KafkaHealthMetric(
    @Context private val kafkaAdminService: KafkaAdminService,
) : HealthService.Metric("kafka") {
    override suspend fun computeStatus(): HealthService.Status = try {
        // Use dedicated dispatcher for health checks to avoid competing with regular traffic
        // This isolates health checks from regular requests at the coroutine level
        withContext(healthCheckDispatcher) {
            kafkaAdminService.containsTopic("health")
        }
        HealthService.Status.UP
    } catch (ex: Throwable) {
        HealthService.Status.DOWN
    }

    override suspend fun computeMetrics(): Map<String, Any> = mapOf("status" to computeStatus())

    companion object {
        // Dedicated thread pool for health checks which isolates health checks from regular traffic
        private val healthCheckDispatcher: CoroutineDispatcher =
            Executors.newFixedThreadPool(2) { r ->
                Thread(r, "health-check-thread").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }
}
