package org.radarbase.gateway

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.radarbase.gateway.inject.PushIntegrationEnhancerFactory
import org.radarbase.jersey.enhancer.EnhancerFactory
import org.radarbase.push.integration.garmin.user.GarminUserRepository
import org.radarbase.googlehealth.user.GoogleHealthUserRepository
import org.radarbase.jersey.config.ConfigLoader.copyEnv
import org.radarbase.jersey.config.ConfigLoader.copyOnChange
import java.net.URI
import java.time.Duration
import java.time.Instant

data class Config(
    /** Radar-jersey resource configuration class. */
    val resourceConfig: Class<out EnhancerFactory> = PushIntegrationEnhancerFactory::class.java,
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
        pushIntegration.validate()
    }

    fun withEnv() = this.copyOnChange(pushIntegration, {
        it.withEnv()
    }, {
        copy(pushIntegration = it)
    })
}

data class PushIntegrationConfig(
    val garmin: GarminConfig = GarminConfig(),
    val googlehealth: GoogleHealthConfig = GoogleHealthConfig(),
) {
    fun withEnv() = this.copyOnChange(googlehealth, {
        it.withEnv()
    }, {
        copy(googlehealth = it)
    })

    fun validate() {
        garmin.validate()
        googlehealth.validate()
    }
}

data class GarminConfig(
    val enabled: Boolean = false,
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val backfill: BackfillConfig = BackfillConfig(),
    val userRepositoryClass: String = "org.radarbase.push.integration.garmin.user.GarminServiceUserRepository",
    val userRepositoryUrl: String = "http://localhost:8080/",
    val userRepositoryClientId: String = "radar_pushendpoint",
    val userRepositoryClientSecret: String = "",
    val userRepositoryTokenUrl: String = "http://localhost:8080/token/",
    val dailiesTopicName: String = "push_garmin_daily_summary",
    val activitiesTopicName: String = "push_garmin_activity_summary",
    val activityDetailsTopicName: String = "push_garmin_activity_detail",
    val epochSummariesTopicName: String = "push_garmin_epoch_summary",
    val sleepsTopicName: String = "push_garmin_sleep_summary",
    val bodyCompositionsTopicName: String = "push_garmin_body_composition",
    val stressTopicName: String = "push_garmin_stress_detail_summary",
    val userMetricsTopicName: String = "push_garmin_user_metrics",
    val moveIQTopicName: String = "push_garmin_move_iq_summary",
    val pulseOXTopicName: String = "push_garmin_pulse_ox",
    val respirationTopicName: String = "push_garmin_respiration",
    val activityDetailsSampleTopicName: String = "push_garmin_activity_detail_sample",
    val bodyBatterySampleTopicName: String = "push_garmin_body_battery_sample",
    val heartRateSampleTopicName: String = "push_garmin_heart_rate_sample",
    val sleepLevelTopicName: String = "push_garmin_sleep_level",
    val stressLevelTopicName: String = "push_garmin_stress_level",
    val sleepScoreTopicName: String = "push_garmin_sleep_score",
    val bloodPressureTopicName: String = "push_garmin_blood_pressure",
    val healthSnapshotTopicName: String = "push_garmin_health_snapshot_summary",
    val heartRateVariabilityTopicName: String = "push_garmin_heart_rate_variability",
    val heartRateVariabilitySampleTopicName: String = "push_garmin_heart_rate_variability_sample"
) {
    val userRepository: Class<*> = Class.forName(userRepositoryClass)

    fun validate() {
        if (enabled) {
            check(GarminUserRepository::class.java.isAssignableFrom(userRepository)) {
                "$userRepositoryClass is not valid. Please specify a class that is a subclass of" + " `org.radarbase.push.integration.garmin.user.GarminUserRepository`"
            }
        }
    }
}

data class BackfillConfig(
    val enabled: Boolean = false,
    val redis: RedisConfig = RedisConfig(),
    val maxThreads: Int = 4,
    val defaultEndDate: Instant = Instant.MAX,
    val userBackfill: List<UserBackfillConfig> = emptyList(),
    val requestsPerUserPerIteration: Int = 40,
    val iterationIntervalMinutes: Long = 5,
    val activitiesEnabled: Boolean = true,
    val activityDetailsEnabled: Boolean = true,
    val bodyCompositionsEnabled: Boolean = true,
    val dailiesEnabled: Boolean = true,
    val epochSummariesEnabled: Boolean = true,
    val pulseOXEnabled: Boolean = true,
    val sleepsEnabled: Boolean = true,
    val stressEnabled: Boolean = true,
    val userMetricsEnabled: Boolean = true,
    val moveIQEnabled: Boolean = true,
    val respirationEnabled: Boolean = true,
    val bloodPressureEnabled: Boolean = false,
    val healthSnapshotEnabled: Boolean = false,
    val heartRateVariabilityEnabled: Boolean = false,
)

data class RedisConfig(
    val uri: URI = URI("redis://localhost:6379"), val lockPrefix: String = "radar-push-garmin/lock"
)

data class UserBackfillConfig(
    val userId: String, val startDate: Instant, val endDate: Instant
)

/** How Google creates per-user subscriptions for this deployment's subscriber. */
enum class SubscriptionCreatePolicy { MANUAL, AUTOMATIC }

data class GoogleHealthConfig(
    val enabled: Boolean = false,
    val userRepositoryClass: String = "org.radarbase.push.integration.google.user.GoogleHealthServiceUserRepository",
    val userRepositoryUrl: String = "http://localhost:8080/",
    val userRepositoryClientId: String = "radar_pushendpoint",
    val userRepositoryClientSecret: String = "",
    val userRepositoryTokenUrl: String = "http://localhost:8080/token/",
    val apiBaseUrl: String = "https://health.googleapis.com/v4",
    val googleCloudProjectId: String = "",
    val subscriberId: String = "radar-pep",
    val subscriberEndpointUri: String = "",
    val subscriberSecret: String = "",
    val serviceAccountKeyPath: String? = null,
    val subscriptionCreatePolicy: SubscriptionCreatePolicy = SubscriptionCreatePolicy.MANUAL,
    val subscriptionReconcileEnabled: Boolean = true,
    val subscriptionReconcileIntervalMinutes: Long = 5,
    /**
     * If a single deletion would delete more subscriptions than this, it skips
     * deletion and alarms instead
     */
    val subscriptionReconcileMaxDeletesPerPass: Int = 50,
    val triggerDataTypes: List<String> = listOf(
        "steps", "sleep", "exercise", "daily-resting-heart-rate", "heart-rate", "daily-sleep-temperature-derivations",
        "heart-rate-variability", "total-calories", "respiratory-rate-sleep-summary",
    ),
    val enabledDataTypes: List<String> = listOf(
        "steps", "heart-rate", "heart-rate-variability", "oxygen-saturation",
        "total-calories", "daily-resting-heart-rate", "respiratory-rate-sleep-summary",
        "daily-sleep-temperature-derivations", "sleep", "exercise",
        "electrocardiogram", "irregular-rhythm-notification",
    ),
    val stepsTopicName: String = "push_googlehealth_steps",
    val heartRateTopicName: String = "push_googlehealth_heart_rate",
    val heartRateVariabilityTopicName: String = "push_googlehealth_heart_rate_variability",
    val oxygenSaturationTopicName: String = "push_googlehealth_oxygen_saturation",
    val totalCaloriesTopicName: String = "push_googlehealth_total_calories",
    val dailyRestingHeartRateTopicName: String = "push_googlehealth_daily_resting_heart_rate",
    val respiratoryRateSleepSummaryTopicName: String = "push_googlehealth_respiratory_rate_sleep_summary",
    val dailySleepTemperatureDerivationsTopicName: String = "push_googlehealth_daily_sleep_temperature_derivations",
    val sleepStagesTopicName: String = "push_googlehealth_sleep_stage",
    val sleepClassicTopicName: String = "push_googlehealth_sleep_classic",
    val exerciseTopicName: String = "push_googlehealth_exercise",
    val electrocardiogramTopicName: String = "push_googlehealth_electrocardiogram",
    val irregularRhythmNotificationTopicName: String = "push_googlehealth_irregular_rhythm_notification",
    val exerciseTcxTopicName: String = "push_googlehealth_exercise_tcx",

    val exerciseTcxEnabled: Boolean = true,
    val backfill: GoogleHealthBackfillConfig = GoogleHealthBackfillConfig(),
) {
    val userRepository: Class<*> = Class.forName(userRepositoryClass)

    fun withEnv() = this.copyEnv("GOOGLE_HEALTH_SERVICE_ACCOUNT_PATH") {
        copy(serviceAccountKeyPath = it)
    }

    fun validate() {
        if (enabled) {
            check(googleCloudProjectId.isNotEmpty()) {
                "googleCloudProjectId must be set when google health is enabled"
            }
            check(subscriberEndpointUri.isNotEmpty()) {
                "subscriberEndpointUri must be set when google health is enabled"
            }
            check(subscriberSecret.isNotEmpty()) {
                "subscriberSecret must be set when google health is enabled"
            }
            if (subscriptionCreatePolicy == SubscriptionCreatePolicy.AUTOMATIC) {
                check(!subscriptionReconcileEnabled) {
                    "subscriptionReconcileEnabled must be false with an AUTOMATIC subscriber — " + "automatic subscriptions can't be listed or deleted, so reconcile cannot manage them."
                }
            }
            check(GoogleHealthUserRepository::class.java.isAssignableFrom(userRepository)) {
                "$userRepositoryClass is not valid. Please specify a class that is a subclass of" + " `org.radarbase.googlehealth.user.GoogleHealthUserRepository`"
            }
        }
    }
}

data class GoogleHealthBackfillConfig(
    val enabled: Boolean = false,
    val redis: RedisConfig = RedisConfig(
        uri = URI("redis://localhost:6379"),
        lockPrefix = "radar-push-googlehealth/lock",
    ),
    val maxThreads: Int = 4,
    val maxBackfillPeriod: Duration = Duration.ofDays(MAX_BACKFILL_DAYS),
    val chunkSizeDays: Long = 7,
    val iterationIntervalMinutes: Long = 10,
) {
    companion object {
        private const val MAX_BACKFILL_DAYS = 365 * 2L
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
        producer = producerDefaults + producer, admin = mutableMapOf<String, Any>().apply {
            producer[BOOTSTRAP_SERVERS_CONFIG]?.let {
                this[BOOTSTRAP_SERVERS_CONFIG] = it
            }
            this += adminDefaults
            this += admin
        }, serialization = serializationDefaults + serialization
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
            "default.api.timeout.ms" to 6000, "request.timeout.ms" to 3000, "retries" to 5
        )

        private val serializationDefaults = mapOf<String, Any>(
            MAX_SCHEMAS_PER_SUBJECT_CONFIG to 10_000
        )
    }
}
