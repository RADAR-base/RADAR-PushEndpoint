package org.radarbase.gateway.inject

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig.USER_INFO_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_USER_INFO_CONFIG
import okhttp3.Credentials
import okhttp3.Headers.Companion.headersOf
import org.radarbase.config.ServerConfig
import org.radarbase.gateway.Config
import org.radarbase.producer.rest.RestClient
import org.radarbase.producer.rest.SchemaRetriever
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import jakarta.ws.rs.core.Context

/** Creates a Schema Retriever based on the current schema registry configuration. */
class SchemaRetrieverFactory(
        @Context private val config: Config
): Supplier<SchemaRetriever> {
    override fun get(): SchemaRetriever {
        val server = when (val schemaRegistryUrl = config.kafka.serialization[SCHEMA_REGISTRY_URL_CONFIG]) {
            is String -> ServerConfig(schemaRegistryUrl)
            is List<*> -> ServerConfig(schemaRegistryUrl.first() as String)
            else -> throw IllegalStateException("Configuration does not contain valid schema.registry.url")
        }
        @Suppress("DEPRECATION")
        val basicCredentials = (config.kafka.serialization[SCHEMA_REGISTRY_USER_INFO_CONFIG].takeIf { it is String && it.isNotEmpty() }
                ?: config.kafka.serialization[USER_INFO_CONFIG]).takeIf { it is String && it.isNotEmpty() } as String?

        val headers = if (basicCredentials != null && basicCredentials.contains(':')) {
            val (username, password) = basicCredentials.split(':', limit = 2)
            headersOf("Authorization", Credentials.basic(username, password))
        } else headersOf()

        return SchemaRetriever(RestClient.global()
                .server(server)
                .headers(headers)
                .timeout(30, TimeUnit.SECONDS)
                .build(), 300)
    }
}
