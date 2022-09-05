package core.log.service;

import core.framework.inject.Inject;
import core.framework.log.Markers;
import core.framework.log.message.ActionLogMessage;
import core.framework.search.BulkIndexRequest;
import core.framework.search.ElasticSearchType;
import core.framework.search.IndexRequest;
import core.framework.util.Maps;
import core.framework.util.Strings;
import core.log.LogGroupConfig;
import core.log.domain.ActionDocument;
import core.log.domain.TraceDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @author neo
 */
public class ActionService {
    private final Logger logger = LoggerFactory.getLogger(ActionService.class);
    private final Map<String, String> applicationGroupMapping = Maps.newHashMap();

    @Inject
    IndexService indexService;
    @Inject
    ElasticSearchType<ActionDocument> actionType;
    @Inject
    ElasticSearchType<TraceDocument> traceType;

    public void applicationGroupMapping(LogGroupConfig logGroupConfig) {
        logGroupConfig.groups.forEach((group, apps) -> apps.forEach(app -> {
            String previous = applicationGroupMapping.put(app, group);
            if (previous != null) {
                logger.warn(Markers.errorCode("DUPLICATED_APP_DETECTED"), "duplicated app {} detected, current group {}, previous group {}", app, group, previous);
            }
        }));
    }

    public void index(List<ActionLogMessage> messages) {
        index(messages, LocalDate.now());
    }

    public void index(ActionLogMessage message) {
        index(message, LocalDate.now());
    }

    void index(List<ActionLogMessage> messages, LocalDate now) {
        if (messages.size() <= 5) { // use single index in quiet time
            for (ActionLogMessage message : messages) {
                index(message, now);
            }
        } else {
            Map<String, Map<String, ActionDocument>> appActions = Maps.newHashMap();
            Map<String, TraceDocument> traces = Maps.newHashMap();
            for (ActionLogMessage message : messages) {
                appActions.computeIfAbsent(message.app, k -> Maps.newHashMap()).put(message.id, action(message));
                if (message.traceLog != null) {
                    traces.put(message.id, trace(message));
                }
            }
            appActions.forEach((app, actions) -> indexActions(app, actions, now));
            if (!traces.isEmpty()) {
                indexTraces(traces, now);
            }
        }
    }

    private void index(ActionLogMessage message, LocalDate now) {
        indexAction(message.id, action(message), now);
        if (message.traceLog != null) {
            indexTrace(message.id, trace(message), now);
        }
    }

    private void indexAction(String id, ActionDocument action, LocalDate now) {
        IndexRequest<ActionDocument> request = new IndexRequest<>();
        request.index = indexService.indexName(actionIndexName(action.app), now);
        request.id = id;
        request.source = action;
        actionType.index(request);
    }

    private void indexTrace(String id, TraceDocument trace, LocalDate now) {
        IndexRequest<TraceDocument> request = new IndexRequest<>();
        request.index = indexService.indexName("trace", now);
        request.id = id;
        request.source = trace;
        traceType.index(request);
    }

    private void indexActions(String app, Map<String, ActionDocument> actions, LocalDate now) {
        try {
            BulkIndexRequest<ActionDocument> request = new BulkIndexRequest<>();
            request.index = indexService.indexName(actionIndexName(app), now);
            request.sources = actions;
            actionType.bulkIndex(request);
        } catch (Exception e) {
            logger.error(Markers.errorCode("BULK_INDEX_FAILED"), Strings.format("bulk index for {} failed", app), e);
        }
    }

    private void indexTraces(Map<String, TraceDocument> traces, LocalDate now) {
        BulkIndexRequest<TraceDocument> request = new BulkIndexRequest<>();
        request.index = indexService.indexName("trace", now);
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
        document.stats = message.stats;
        document.performanceStats = message.performanceStats;
        document.info = message.info;
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

    String actionIndexName(String application) {
        String group = applicationGroupMapping.get(application);
        if (group == null) {
            return "action";
        }
        return "action-" + group;
    }
}
