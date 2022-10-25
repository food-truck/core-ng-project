package core.framework.internal.kafka;

import core.framework.util.Maps;
import core.framework.util.StopWatch;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.security.scram.internals.ScramMechanism;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author neo
 */
public class MessageProducer {
    public final ProducerMetrics producerMetrics;
    private final Logger logger = LoggerFactory.getLogger(MessageProducer.class);
    private final KafkaURI uri;
    private final String name;
    private final int maxRequestSize;
    private Producer<byte[], byte[]> producer;
    private final String scram256JaasConfig;

    public MessageProducer(KafkaURI uri, String name, int maxRequestSize, String scram256JaasConfig) {
        this.uri = uri;
        this.name = name;
        this.maxRequestSize = maxRequestSize;
        this.producerMetrics = new ProducerMetrics(name);
        this.scram256JaasConfig = scram256JaasConfig;
    }

    public void initialize() {
        if (scram256JaasConfig == null) {
            producer = createProducer(uri);
        } else {
            producer = createScram256Producer(uri);
        }
    }

    public void send(ProducerRecord<byte[], byte[]> record) {
        producer.send(record, new KafkaCallback(record));
    }

    Producer<byte[], byte[]> createProducer(KafkaURI uri) {
        var watch = new StopWatch();
        try {
            Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, uri.bootstrapURIs,
                ProducerConfig.COMPRESSION_TYPE_CONFIG, CompressionType.SNAPPY.name,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000,                           // 60s, DELIVERY_TIMEOUT_MS_CONFIG is INT type
                ProducerConfig.LINGER_MS_CONFIG, 5L,                                         // use small linger time within acceptable range to improve batching
                ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 500L,                            // longer backoff to reduce cpu usage when kafka is not available
                ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 5_000L,                      // 5s
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 30_000L,                                 // 30s, metadata update timeout, shorter than default, to get exception sooner if kafka is not available
                ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);

            var serializer = new ByteArraySerializer();
            var producer = new KafkaProducer<>(config, serializer, serializer);
            producerMetrics.set(producer.metrics());
            return producer;
        } finally {
            logger.info("create kafka producer, uri={}, name={}, elapsed={}", uri, name, watch.elapsed());
        }
    }

    Producer<byte[], byte[]> createScram256Producer(KafkaURI uri) {
        var watch = new StopWatch();
        try {
            Map<String, Object> config = Maps.newHashMapWithExpectedSize(11);
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, uri.bootstrapURIs);
            config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, CompressionType.SNAPPY.name);
            config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000);                          // 60s, DELIVERY_TIMEOUT_MS_CONFIG is INT type
            config.put(ProducerConfig.LINGER_MS_CONFIG, 5L);                                        // use small linger time within acceptable range to improve batching
            config.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 500L);                           // longer backoff to reduce cpu uxsage when kafka is not available
            config.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 5_000L);                     // 5s
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 30_000L);                                // 30s, metadata update timeout, shorter than default, to get exception soonxer if kafka is not available
            config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
            config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name());
            config.put(SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_256.mechanismName());
            config.put(SaslConfigs.SASL_JAAS_CONFIG, scram256JaasConfig);

            var serializer = new ByteArraySerializer();
            var producer = new KafkaProducer<>(config, serializer, serializer);
            producerMetrics.set(producer.metrics());
            return producer;
        } finally {
            logger.info("create kafka producer, uri={}, name={}, elapsed={}", uri, name, watch.elapsed());
        }
    }

    public void close(long timeoutInMs) {
        if (producer != null) {
            logger.info("close kafka producer, uri={}, name={}", uri, name);
            producer.flush();
            producer.close(Duration.ofMillis(timeoutInMs));    // close timeout must greater than 0, the shutdown hook always pass in positive timeout
        }
    }

    static final class KafkaCallback implements Callback {
        private static final Logger LOGGER = LoggerFactory.getLogger(KafkaCallback.class);
        private final ProducerRecord<byte[], byte[]> record;

        KafkaCallback(ProducerRecord<byte[], byte[]> record) {
            this.record = record;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (exception != null) {    // if failed to send message (kafka is down), fallback to error output
                byte[] key = record.key();
                LOGGER.error("failed to send kafka message, error={}, topic={}, key={}, value={}",
                    exception.getMessage(),
                    record.topic(),
                    key == null ? null : new String(key, UTF_8),
                    new String(record.value(), UTF_8),
                    exception);
            }
        }
    }
}
