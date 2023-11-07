package core.log.service;

import core.framework.inject.Inject;
import core.framework.kafka.MessagePublisher;
import core.framework.log.LogAppender;
import core.framework.log.message.ActionLogMessage;
import core.framework.log.message.StatMessage;

import java.util.Objects;

/**
 * @author miller
 * Currently, we still need a way to get failed action of log-processor
 */
public final class KafkaExtensionAppender implements LogAppender {
    @Inject
    MessagePublisher<ActionLogMessage> publisher;
    private final LogAppender delegate;

    public KafkaExtensionAppender(LogAppender delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null!");
        this.delegate = delegate;
    }

    @Override
    public void append(ActionLogMessage message) {
        delegate.append(message);

        if (message.traceLog != null) {
            publisher.publish(message);
        }
    }

    @Override
    public void append(StatMessage message) {
        delegate.append(message);
    }
}
