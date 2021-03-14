package core.framework.module;

import core.framework.http.HTTPMethod;
import core.framework.internal.inject.InjectValidator;
import core.framework.internal.kafka.KafkaURI;
import core.framework.internal.kafka.MessageListener;
import core.framework.internal.kafka.MessageProducer;
import core.framework.internal.kafka.MessagePublisherImpl;
import core.framework.internal.kafka.SASLConfig;
import core.framework.internal.module.Config;
import core.framework.internal.module.ModuleContext;
import core.framework.internal.module.ShutdownHook;
import core.framework.internal.web.management.KafkaController;
import core.framework.kafka.BulkMessageHandler;
import core.framework.kafka.MessageHandler;
import core.framework.kafka.MessagePublisher;
import core.framework.util.Strings;
import core.framework.util.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
public class KafkaConfig extends Config {
    private final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);
    String name;
    MessageProducer producer;
    private ModuleContext context;
    private KafkaURI uri;
    private MessageListener listener;
    private SASLConfig saslConfig;
    private boolean handlerAdded;
    private int maxRequestSize = 1024 * 1024;   // default 1M, refer to org.apache.kafka.clients.producer.ProducerConfig.MAX_REQUEST_SIZE_CONFIG

    @Override
    protected void initialize(ModuleContext context, String name) {
        this.context = context;
        this.name = name;
    }

    @Override
    protected void validate() {
        if (!handlerAdded)
            throw new Error("kafka is configured, but no producer/consumer added, please remove unnecessary config, name=" + name);
    }

    public void uri(String uri) {
        if (this.uri != null)
            throw new Error(format("kafka uri is already configured, name={}, uri={}, previous={}", name, uri, this.uri));
        this.uri = new KafkaURI(uri);
    }

    public void sasl(String jaasConfig) {
        if (Strings.isBlank(jaasConfig))
            throw new Error("JAAS must be configured with security protocol SASL/PLAIN");
        this.saslConfig = new SASLConfig(jaasConfig);
    }

    // for use case as replying message back to publisher, so the topic can be dynamic (different services (consumer group) expect to receive reply in their own topic)
    public <T> MessagePublisher<T> publish(Class<T> messageClass) {
        return publish(null, messageClass);
    }

    public <T> MessagePublisher<T> publish(String topic, Class<T> messageClass) {
        logger.info("publish, topic={}, messageClass={}, name={}", topic, messageClass.getTypeName(), name);
        if (uri == null) throw new Error("kafka uri must be configured first, name=" + name);
        context.beanClassValidator.validate(messageClass);
        MessagePublisher<T> publisher = createMessagePublisher(topic, messageClass);
        context.beanFactory.bind(Types.generic(MessagePublisher.class, messageClass), name, publisher);
        handlerAdded = true;
        return publisher;
    }

    <T> MessagePublisher<T> createMessagePublisher(String topic, Class<T> messageClass) {
        if (producer == null) {
            var producer = new MessageProducer(uri, saslConfig, name, maxRequestSize);
            producer.tryCreateProducer();  // try to init kafka during startup
            context.collector.metrics.add(producer.producerMetrics);
            context.shutdownHook.add(ShutdownHook.STAGE_4, producer::close);
            var controller = new KafkaController(producer);
            context.route(HTTPMethod.POST, managementPathPattern("/topic/:topic/message/:key"), (LambdaController) controller::publish, true);
            this.producer = producer;
        }
        return new MessagePublisherImpl<>(producer, topic, messageClass);
    }

    String managementPathPattern(String postfix) {
        var builder = new StringBuilder("/_sys/kafka");
        if (name != null) builder.append('/').append(name);
        builder.append(postfix);
        return builder.toString();
    }

    public <T> void subscribe(String topic, Class<T> messageClass, MessageHandler<T> handler) {
        subscribe(topic, messageClass, handler, null);
    }

    public <T> void subscribe(String topic, Class<T> messageClass, BulkMessageHandler<T> handler) {
        subscribe(topic, messageClass, null, handler);
    }

    private <T> void subscribe(String topic, Class<T> messageClass, MessageHandler<T> handler, BulkMessageHandler<T> bulkHandler) {
        if (handler == null && bulkHandler == null) throw new Error("handler must not be null");
        logger.info("subscribe, topic={}, messageClass={}, handlerClass={}, name={}", topic, messageClass.getTypeName(), handler != null ? handler.getClass().getCanonicalName() : bulkHandler.getClass().getCanonicalName(), name);
        context.beanClassValidator.validate(messageClass);
        new InjectValidator(handler != null ? handler : bulkHandler).validate();
        listener().subscribe(topic, messageClass, handler, bulkHandler);
        handlerAdded = true;
    }

    private MessageListener listener() {
        if (listener == null) {
            if (uri == null) throw new Error("kafka uri must be configured first, name=" + name);
            var listener = new MessageListener(uri, saslConfig, name, context.logManager);
            context.startupHook.add(listener::start);
            context.shutdownHook.add(ShutdownHook.STAGE_0, timeout -> listener.shutdown());
            context.shutdownHook.add(ShutdownHook.STAGE_1, listener::awaitTermination);
            context.collector.metrics.add(listener.consumerMetrics);
            this.listener = listener;   // make lambda not refer to this class/field
        }
        return listener;
    }

    // by default listener use AppName as consumer group
    // e.g. use Network.LOCAL_HOST_NAME to make every pod receives messages from topic, (local cache invalidation, web socket notification)
    // use "${service-name}-${label}" to allow same service to be deployed for mutlitenancy
    public void groupId(String groupId) {
        listener().groupId = groupId;
    }

    public void poolSize(int poolSize) {
        listener().poolSize = poolSize;
    }

    public void maxProcessTime(Duration maxProcessTime) {
        listener().maxProcessTime = maxProcessTime;
    }

    // to increase max message size, it must change on both producer and broker sides
    // on broker size use "--override message.max.bytes=size"
    // refer to https://kafka.apache.org/documentation/#message.max.bytes
    public void maxRequestSize(int size) {
        if (size <= 0) throw new Error("max request size must be greater than 0, value=" + size);
        if (producer != null) throw new Error("kafka().maxRequestSize() must be configured before adding publisher");
        maxRequestSize = size;
    }

    public void longConsumerDelayThreshold(Duration threshold) {
        listener().longConsumerDelayThreshold = threshold;
    }

    public void maxPoll(int maxRecords, int maxBytes) {
        if (maxRecords <= 0) throw new Error("max poll records must be greater than 0, value=" + maxRecords);
        if (maxBytes <= 0) throw new Error("max poll bytes must be greater than 0, value=" + maxBytes);
        MessageListener listener = listener();
        listener.maxPollRecords = maxRecords;
        listener.maxPollBytes = maxBytes;
    }

    public void minPoll(int minBytes, Duration maxWaitTime) {
        if (minBytes <= 0) throw new Error("min poll bytes must be greater than 0, value=" + minBytes);
        if (maxWaitTime == null || maxWaitTime.toMillis() <= 0) throw new Error("max wait time must be greater than 0, value=" + maxWaitTime);
        MessageListener listener = listener();
        listener.minPollBytes = minBytes;
        listener.maxWaitTime = maxWaitTime;
    }
}
