package core.log.kafka;

import core.framework.inject.Inject;
import core.framework.kafka.BulkMessageHandler;
import core.framework.kafka.Message;
import core.framework.log.message.ActionLogMessage;
import core.framework.search.BulkIndexRequest;
import core.framework.search.ElasticSearchType;
import core.framework.util.Maps;
import core.log.LogIndexRouter;
import core.log.domain.ActionDocument;
import core.log.domain.TraceDocument;
import core.log.service.ActionLogForwarder;
import core.log.service.IndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author neo
 */
public class ActionLogMessageHandler implements BulkMessageHandler<ActionLogMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionLogMessageHandler.class);
    @Nullable
    final ActionLogForwarder forwarder;

    private final LogIndexRouter actionLogIndexRouter;

    private final LogIndexRouter traceLogIndexRouter;

    @Inject
    IndexService indexService;
    @Inject
    ElasticSearchType<ActionDocument> actionType;
    @Inject
    ElasticSearchType<TraceDocument> traceType;

    public ActionLogMessageHandler(@Nullable ActionLogForwarder forwarder, LogIndexRouter actionLogIndexRouter, LogIndexRouter traceLogIndexRouter) {
        this.forwarder = forwarder;
        this.actionLogIndexRouter = actionLogIndexRouter;
        this.traceLogIndexRouter = traceLogIndexRouter;
    }

    @Override
    public void handle(List<Message<ActionLogMessage>> messages) {
        index(messages, LocalDate.now());

        if (forwarder != null) forwarder.forward(messages);
    }

    void index(List<Message<ActionLogMessage>> messages, LocalDate now) {
        Map<String, ActionDocument> actions = Maps.newHashMapWithExpectedSize(messages.size());
        Map<String, TraceDocument> traces = Maps.newHashMap();
        for (Message<ActionLogMessage> message : messages) {
            ActionLogMessage value = message.value;
            actions.put(value.id, action(value));
            if (value.traceLog != null) {
                traces.put(value.id, trace(value));
            }
        }
        indexActions(actions, now);
        if (!traces.isEmpty()) {
            indexTraces(traces, now);
        }
    }

    private void indexActions(Map<String, ActionDocument> actions, LocalDate now) {
        actions.entrySet().stream()
            .collect(Collectors.groupingBy(entry -> Objects.requireNonNullElse(entry.getValue().app, "")))
            .forEach((app, appActions) -> {
                try {
                    BulkIndexRequest<ActionDocument> request = new BulkIndexRequest<>();
                    request.index = indexService.indexName(actionLogIndexRouter.route(app), now);
                    request.sources = appActions.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    actionType.bulkIndex(request);
                } catch (Exception e) {
                    LOGGER.error("failure indexActions! errorMsg: " + e.getMessage(), e);
                }
            });
    }

    private void indexTraces(Map<String, TraceDocument> traces, LocalDate now) {
        BulkIndexRequest<TraceDocument> request = new BulkIndexRequest<>();
        request.index = indexService.indexName(traceLogIndexRouter.route("trace"), now);
        request.sources = traces;
        traceType.bulkIndex(request);
    }

    private ActionDocument action(ActionLogMessage message) {
        var document = new ActionDocument();
        document.timestamp = message.date;
        document.id = message.id;
        document.app = message.app;
        document.host = message.host;
        document.result = message.result;
        document.action = message.action;
        document.refIds = message.refIds;
        document.clients = message.clients;
        document.correlationIds = message.correlationIds;
        document.errorCode = message.errorCode;
        document.errorMessage = message.errorMessage;
        document.elapsed = message.elapsed;
        document.context = message.context;
        document.info = message.info;
        document.stats = message.stats;
        document.performanceStats = message.performanceStats;
        return document;
    }

    private TraceDocument trace(ActionLogMessage message) {
        var document = new TraceDocument();
        document.timestamp = message.date;
        document.app = message.app;
        document.action = message.action;
        document.result = message.result;
        document.errorCode = message.errorCode;
        document.content = message.traceLog;
        return document;
    }
}
