package org.radarbase.gateway.inject

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig.USER_INFO_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_USER_INFO_CONFIG
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import jakarta.ws.rs.core.Context
import org.radarbase.config.ServerConfig
import org.radarbase.gateway.Config
import org.radarbase.producer.io.timeout
import org.radarbase.producer.schema.SchemaRetriever
import org.radarbase.producer.schema.SchemaRetriever.Companion.schemaRetriever
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

/** Creates a Schema Retriever based on the current schema registry configuration. */
class SchemaRetrieverFactory(
    @Context private val config: Config,
) : Supplier<SchemaRetriever> {
    override fun get(): SchemaRetriever {
        val server = when (val schemaRegistryUrl = config.kafka.serialization[SCHEMA_REGISTRY_URL_CONFIG]) {
            is String -> ServerConfig(schemaRegistryUrl)
            is List<*> -> ServerConfig(schemaRegistryUrl.first() as String)
            else -> throw IllegalStateException("Configuration does not contain valid schema.registry.url")
        }

        @Suppress("DEPRECATION")
        val basicCredentials =
            config.kafka.serialization[SCHEMA_REGISTRY_USER_INFO_CONFIG].asNonEmptyString()
                ?: config.kafka.serialization[USER_INFO_CONFIG].asNonEmptyString()

        return schemaRetriever(baseUrl = server.urlString) {
            httpClient = HttpClient(CIO) {
                timeout(30.seconds)
                if (basicCredentials != null && basicCredentials.contains(':')) {
                    val (apiKey, apiSecret) = basicCredentials.split(':', limit = 2)
                    install(Auth) {
                        basic {
                            sendWithoutRequest { true }
                            credentials {
                                BasicAuthCredentials(username = apiKey, password = apiSecret)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private fun Any?.asNonEmptyString(): String? = (this as? String)?.takeIf { it.isNotEmpty() }
    }
}
