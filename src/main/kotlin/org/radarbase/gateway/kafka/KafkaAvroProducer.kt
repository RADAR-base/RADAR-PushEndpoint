package org.radarbase.gateway.kafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.IndexedRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.radarbase.gateway.Config
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ExecutionException

class KafkaAvroProducer(
    config: Config,
    schemaRegistryClient: SchemaRegistryClient
) : Closeable {
    private val producer: Producer<Any, Any>

    init {
        logger.info("Creating Kafka producer")

        val keySerializer = KafkaAvroSerializer(schemaRegistryClient).apply {
            configure(config.kafka.serialization, true)
        }
        val valueSerializer = KafkaAvroSerializer(schemaRegistryClient).apply {
            configure(config.kafka.serialization, false)
        }
        producer = KafkaProducer(config.kafka.producer, keySerializer, valueSerializer)
        logger.info("Created Kafka producer")
    }

    @Throws(KafkaException::class)
    fun produce(topic: String, records: List<Pair<IndexedRecord, IndexedRecord>>) {
        records
            .map { (key, value) -> producer.send(ProducerRecord(topic, key, value)) }
            .forEach {
                try {
                    it.get() // asserts that the send completed and was successful
                } catch (ex: ExecutionException) {
                    throw ex.cause!!
                }
            }
    }

    override fun close() {
        producer.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAvroProducer::class.java)
    }
}
