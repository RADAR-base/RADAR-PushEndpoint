package org.radarbase.gateway

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.radarbase.gateway.inject.PushIntegrationEnhancerFactory
import org.radarbase.jersey.config.EnhancerFactory
import org.radarbase.push.integration.common.user.UserRepository
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

data class Config(
    /** Radar-jersey resource configuration class. */
    val resourceConfig: Class<out EnhancerFactory> = PushIntegrationEnhancerFactory::class.java,
    /** Authorization configurations. */
    val auth: AuthConfig = AuthConfig(),
    /** Kafka configurations. */
    val kafka: KafkaConfig = KafkaConfig(),
    /** Server configurations. */
    val server: GatewayServerConfig = GatewayServerConfig(),
    /** Push integration configs **/
    val pushIntegration: PushIntegrationConfig = PushIntegrationConfig()
) {
    /** Fill in some default values for the configuration. */
    fun withDefaults(): Config = copy(kafka = kafka.withDefaults())

    /**
     * Validate the configuration.
     * @throws IllegalStateException if the configuration is incorrect
     */
    fun validate() {
        kafka.validate()
        auth.validate()
        pushIntegration.validate()
    }
}

data class PushIntegrationConfig(
    val enabledServices: List<String> = listOf("garmin"),
    val garmin: GarminConfig = GarminConfig()
) {
    fun validate() {
        garmin.validate()
    }
}

data class GarminConfig(
    val userRepositoryClass: String =
        "org.radarbase.push.integration.garmin.user.ServiceUserRepository",
    val dailiesTopicName: String = "push_integration_garmin_daily",
    val activitiesTopicName: String = "push_integration_garmin_activity",
    val activityDetailsTopicName: String = "push_integration_garmin_activity_detail",
    val manualActivitiesTopicName: String = "push_integration_garmin_manual_activity",
    val epochSummariesTopicName: String = "push_integration_garmin_epoch_summary",
    val sleepsTopicName: String = "push_integration_garmin_sleep",
    val bodyCompositionsTopicName: String = "push_integration_garmin_body_composition",
    val stressTopicName: String = "push_integration_garmin_stress",
    val userMetricsTopicName: String = "push_integration_garmin_user_metrics",
    val moveIQTopicName: String = "push_integration_garmin_move_iq",
    val pulseOXTopicName: String = "push_integration_garmin_pulse_ox",
    val respirationTopicName: String = "push_integration_garmin_respiration"
) {
    val userRepository: Class<*> = Class.forName(userRepositoryClass)

    fun validate() {
        check(UserRepository::class.java.isAssignableFrom(userRepository)) {
            "$userRepositoryClass is not valid. Please specify a class that is a subclass of" +
                    " `org.radarbase.push.integrations.common.user.UserRepository`"
        }
    }
}

data class GatewayServerConfig(
    /** Base URL to serve data with. This will determine the base path and the port. */
    val baseUri: URI = URI.create("http://0.0.0.0:8090/push/integrations/"),
    /** Maximum number of simultaneous requests. */
    val maxRequests: Int = 200,
    /**
     * Maximum request content length, also when decompressed.
     * This protects against memory overflows.
     */
    val maxRequestSize: Long = 24 * 1024 * 1024,
    /**
     * Whether JMX should be enabled. Disable if not needed, for higher performance.
     */
    val isJmxEnabled: Boolean = true
)

data class KafkaConfig(
    /** Number of Kafka brokers to keep in a pool for reuse in multiple requests. */
    val poolSize: Int = 20,
    /** Kafka producer settings. Read from https://kafka.apache.org/documentation/#producerconfigs. */
    val producer: Map<String, Any> = mapOf(),
    /** Kafka Admin Client settings. Read from https://kafka.apache.org/documentation/#adminclientconfigs. */
    val admin: Map<String, Any> = mapOf(),
    /** Kafka serialization settings, used in KafkaAvroSerializer. Read from [io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig]. */
    val serialization: Map<String, Any> = mapOf()
) {
    fun withDefaults(): KafkaConfig = copy(
        producer = producerDefaults + producer,
        admin = mutableMapOf<String, Any>().apply {
            producer[BOOTSTRAP_SERVERS_CONFIG]?.let {
                this[BOOTSTRAP_SERVERS_CONFIG] = it
            }
            this += adminDefaults
            this += admin
        },
        serialization = serializationDefaults + serialization
    )

    fun validate() {
        check(producer[BOOTSTRAP_SERVERS_CONFIG] is String) { "$BOOTSTRAP_SERVERS_CONFIG missing in kafka: producer: {} configuration" }
        check(admin[BOOTSTRAP_SERVERS_CONFIG] is String) { "$BOOTSTRAP_SERVERS_CONFIG missing in kafka: admin: {} configuration" }
        val schemaRegistryUrl = serialization[SCHEMA_REGISTRY_URL_CONFIG]
        check(schemaRegistryUrl is String || schemaRegistryUrl is List<*>) {
            "$SCHEMA_REGISTRY_URL_CONFIG missing in kafka: serialization: {} configuration"
        }
    }

    companion object {
        private val producerDefaults = mapOf(
            "request.timeout.ms" to 3000,
            "max.block.ms" to 6000,
            "linger.ms" to 10,
            "retries" to 5,
            "acks" to "all",
            "delivery.timeout.ms" to 6000
        )
        private val adminDefaults = mapOf(
            "default.api.timeout.ms" to 6000,
            "request.timeout.ms" to 3000,
            "retries" to 5
        )

        private val serializationDefaults = mapOf<String, Any>(
            MAX_SCHEMAS_PER_SUBJECT_CONFIG to 10_000
        )
    }
}

data class AuthConfig(
    /** OAuth 2.0 resource name. */
    val resourceName: String = "res_gateway",
    /**
     * Whether to check that the user that submits data has the reported source ID registered
     * in the ManagementPortal.
     */
    val checkSourceId: Boolean = true,
    /** OAuth 2.0 token issuer. If null, this is not checked. */
    val issuer: String? = null,
    /**
     * ManagementPortal URL. If available, this is used to read the public key from
     * ManagementPortal directly. This is the recommended method of getting public key.
     */
    val managementPortalUrl: String? = null,
    /** Key store for checking the digital signature of OAuth 2.0 JWTs. */
    val keyStore: KeyStoreConfig = KeyStoreConfig(),
    /** Public keys for checking the digital signature of OAuth 2.0 JWTs. */
    val publicKeys: KeyConfig = KeyConfig()
) {
    fun validate() {
        keyStore.validate()
        check(managementPortalUrl != null || keyStore.isConfigured || publicKeys.isConfigured) {
            "At least one of auth.keyStore, auth.publicKeys or auth.managementPortalUrl must be configured"
        }
    }
}

data class KeyStoreConfig(
    /** Path to the p12 key store. */
    val path: Path? = null,
    /** Key alias in the key store. */
    val alias: String? = null,
    /** Password of the key store. */
    val password: String? = null
) {
    fun validate() {
        if (path != null) {
            check(Files.exists(path)) { "KeyStore configured in auth.keyStore.path does not exist" }
            checkNotNull(alias) { "KeyStore is configured without auth.keyStore.alias" }
            checkNotNull(password) { "KeyStore is configured without auth.keyStore.password" }
        }
    }

    val isConfigured: Boolean = path != null
}

data class KeyConfig(
    /** List of ECDSA public key signatures in PEM format. */
    val ecdsa: List<String>? = null,
    /** List of RSA public key signatures in PEM format. */
    val rsa: List<String>? = null
) {
    val isConfigured: Boolean = !ecdsa.isNullOrEmpty() || !rsa.isNullOrEmpty()
}
