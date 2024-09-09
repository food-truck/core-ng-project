package core.log.kafka;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import core.framework.inject.Inject;
import core.framework.internal.asm.CodeBuilder;
import core.framework.internal.validate.Validator;
import core.framework.kafka.BulkMessageHandler;
import core.framework.kafka.Message;
import core.framework.log.ActionLogContext;
import core.framework.log.message.ActionLogMessage;
import core.framework.search.BulkIndexRequest;
import core.framework.search.ElasticSearchType;
import core.framework.search.SearchException;
import core.framework.util.Maps;
import core.framework.util.StopWatch;
import core.log.LogIndexRouter;
import core.log.domain.ActionDocument;
import core.log.domain.TraceDocument;
import core.log.service.ActionLogForwarder;
import core.log.service.IndexService;
import core.log.service.NetworkErrorRetryService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author neo
 */
public class ActionLogMessageHandler implements BulkMessageHandler<ActionLogMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionLogMessageHandler.class);
    @Nullable
    final ActionLogForwarder forwarder;
    private ElasticsearchClient elasticsearchClient;

    private final Validator<ActionDocument> validator;
    private final LogIndexRouter actionLogIndexRouter;
    private final LogIndexRouter traceLogIndexRouter;

    @Inject
    IndexService indexService;
    @Inject
    ElasticSearchType<ActionDocument> actionType;
    @Inject
    ElasticSearchType<TraceDocument> traceType;
    @Inject
    NetworkErrorRetryService networkErrorRetryService;

    public ActionLogMessageHandler(@Nullable ActionLogForwarder forwarder, LogIndexRouter actionLogIndexRouter, LogIndexRouter traceLogIndexRouter) {
        this.forwarder = forwarder;
        this.actionLogIndexRouter = actionLogIndexRouter;
        this.traceLogIndexRouter = traceLogIndexRouter;
        this.validator = Validator.of(ActionDocument.class);
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
        networkErrorRetryService.run(() -> indexActions(actions, now));
        if (!traces.isEmpty()) {
            networkErrorRetryService.run(() -> indexTraces(traces, now));
        }
    }

    private void indexActions(Map<String, ActionDocument> actions, LocalDate now) {
        var watch = new StopWatch();
        var operations = new ArrayList<BulkOperation>(actions.size());
        actions.forEach((id, action) -> {
            validator.validate(action, false);
            var index = indexService.indexName(actionLogIndexRouter.route(Objects.requireNonNullElse(action.app, "")), now);
            operations.add(BulkOperation.of(builder -> builder.index(i -> i.index(index).id(id).document(action))));
        });
        long esTook = 0;
        try {
            var response = getElasticSearchClient().bulk(builder -> builder.operations(operations));
            esTook = response.took();
            if (!response.errors()) return;

            var builder = new CodeBuilder();
            builder.append("bulk operation failed, errors=[\n");
            for (var item : response.items()) {
                var error = item.error();
                if (error != null) {
                    builder.append("id={}, error={}, causedBy={}, stackTrace={}\n", item.id(), error.reason(), error.causedBy(), error.stackTrace());
                }
            }
            builder.append("]");
            throw new SearchException(builder.build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ElasticsearchException e) {
            var error = e.error();
            var builder = new StringBuilder(e.getMessage());
            builder.append("\nmetadata:\n");
            for (var entry : error.metadata().entrySet()) {
                builder.append(entry).append('\n');
            }
            var causedBy = error.causedBy();
            if (causedBy != null) {
                builder.append("causedBy: ").append(causedBy.reason());
            }
            throw new SearchException(builder.toString(), e);
        } finally {
            long elapsed = watch.elapsed();
            LOGGER.debug("bulkIndex, index=action, size={}, esTook={}, elapsed={}", actions.size(), esTook, elapsed);
            ActionLogContext.track("elasticsearch", elapsed, 0, actions.size());
        }
    }

    private void indexTraces(Map<String, TraceDocument> traces, LocalDate now) {
        BulkIndexRequest<TraceDocument> request = new BulkIndexRequest<>();
        request.index = indexService.indexName(traceLogIndexRouter.route("trace"), now);
        request.sources = traces;
        traceType.bulkIndex(request);
    }

    @SuppressFBWarnings({"RFI_SET_ACCESSIBLE", "REC_CATCH_EXCEPTION"})
    private ElasticsearchClient getElasticSearchClient() {
        if (elasticsearchClient == null) {
            try {
                var elasticSearchField = actionType.getClass().getDeclaredField("elasticSearch");
                elasticSearchField.setAccessible(true);
                var elasticSearch = elasticSearchField.get(actionType);
                var elasticSearchClientField = elasticSearch.getClass().getDeclaredField("client");
                elasticSearchClientField.setAccessible(true);
                elasticsearchClient = (ElasticsearchClient) elasticSearchClientField.get(elasticSearch);
            } catch (Exception e) {
                LOGGER.error("getElasticsearchClient failure!", e);
                throw new IllegalStateException(e);
            }
        }
        return elasticsearchClient;
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
