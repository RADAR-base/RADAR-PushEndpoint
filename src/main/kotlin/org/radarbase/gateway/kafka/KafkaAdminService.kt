package org.radarbase.gateway.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.TopicDescription
import org.radarbase.gateway.Config
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpNotFoundException
import org.radarbase.jersey.util.CacheConfig
import org.radarbase.jersey.util.CachedSet
import org.radarbase.jersey.util.CachedValue
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response

class KafkaAdminService(@Context private val config: Config): Closeable {
    private val adminClient: AdminClient = AdminClient.create(config.kafka.admin)

    private val listCache = CachedSet<String>(listCacheConfig) {
        try {
            adminClient.listTopics()
                    .names()
                    .get(3L, TimeUnit.SECONDS)
                    .filterTo(LinkedHashSet()) { !it.startsWith('_') }
        } catch (ex: Exception) {
            logger.error("Failed to list Kafka topics", ex)
            throw KafkaUnavailableException(ex)
        }
    }
    private val topicInfo: ConcurrentMap<String, CachedValue<TopicInfo>> = ConcurrentHashMap()

    fun containsTopic(topic: String): Boolean = topic in listCache

    fun listTopics(): Collection<String> = listCache.get()

    fun topicInfo(topic: String): TopicInfo {
        if (!containsTopic(topic)) {
            throw HttpNotFoundException("topic_not_found", "Topic $topic does not exist")
        }
        return topicInfo.computeIfAbsent(topic) {
            CachedValue(describeCacheConfig, {
                val topicDescription = try {
                    adminClient.describeTopics(listOf(topic))
                            .values()
                            .values
                            .first()
                            .get(3L, TimeUnit.SECONDS)
                } catch (ex: Exception) {
                    logger.error("Failed to describe topics", ex)
                    throw KafkaUnavailableException(ex)
                }

                topicDescription.toTopicInfo()
            })
        }.get()
    }

    override fun close() = adminClient.close()

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAdminService::class.java)

        private val listCacheConfig = CacheConfig(
                refreshDuration = Duration.ofSeconds(10),
                retryDuration = Duration.ofSeconds(2),
                maxSimultaneousCompute = 3,
        )
        private val describeCacheConfig = CacheConfig(
                refreshDuration = Duration.ofMinutes(30),
                retryDuration = Duration.ofSeconds(2),
                maxSimultaneousCompute = 2,
        )

        private fun org.apache.kafka.common.TopicPartitionInfo.toTopicPartitionInfo(): TopicPartitionInfo {
            return TopicPartitionInfo(partition = partition())
        }

        private fun TopicDescription.toTopicInfo() = TopicInfo(name(), partitions()
                .map { it.toTopicPartitionInfo() })

        class KafkaUnavailableException(ex: Exception)
            : HttpApplicationException(
                Response.Status.SERVICE_UNAVAILABLE,
                "kafka_unavailable",
                ex.message ?: ex.cause?.message ?: ex.javaClass.name)
    }

    data class TopicInfo(
            val name: String,
            val partitions: List<TopicPartitionInfo>,
    )

    data class TopicPartitionInfo(
            val partition: Int,
    )
}
