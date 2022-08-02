package core.log.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import core.framework.inject.Inject;
import core.framework.log.message.ActionLogMessage;
import core.framework.log.message.PerformanceStatMessage;
import core.framework.search.ElasticSearch;
import core.framework.search.ElasticSearchType;
import core.framework.search.GetRequest;
import core.framework.search.SearchRequest;
import core.framework.util.Lists;
import core.log.IntegrationTest;
import core.log.domain.ActionDocument;
import core.log.domain.TraceDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static core.framework.search.query.Queries.match;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author neo
 */
class ActionServiceTest extends IntegrationTest {
    @Inject
    ActionService actionService;
    @Inject
    IndexService indexService;
    @Inject
    ElasticSearchType<ActionDocument> actionType;
    @Inject
    ElasticSearchType<TraceDocument> traceType;
    @Inject
    ElasticSearch elasticSearch;

    @Test
    void index() {
        ActionLogMessage message1 = message("1", "OK");
        message1.context = Map.of("key", List.of("value"));
        message1.info = Map.of("info", List.of("value"));
        message1.stats = Map.of("count", 1d);
        message1.correlationIds = List.of("id1", "id2");
        message1.clients = List.of("client");
        var stat = new PerformanceStatMessage();
        stat.count = 1;
        stat.totalElapsed = 10L;
        stat.readEntries = 1;
        stat.writeEntries = 2;
        message1.performanceStats = Map.of("redis", stat);

        ActionLogMessage message2 = message("2", "WARN");
        message2.traceLog = "trace";

        LocalDate now = LocalDate.of(2016, Month.JANUARY, 15);
        actionService.index(List.of(message1, message2), now);

        ActionDocument action = actionDocument(now, message1.id);
        assertThat(action.timestamp).isEqualTo(message1.date);
        assertThat(action.result).isEqualTo(message1.result);
        assertThat(action.correlationIds).isEqualTo(message1.correlationIds);
        assertThat(action.refIds).isEqualTo(message1.refIds);
        assertThat(action.clients).isEqualTo(message1.clients);
        assertThat(action.performanceStats.get("redis")).usingRecursiveComparison().isEqualTo(message1.performanceStats.get("redis"));
        assertThat(action.context).containsEntry("key", List.of("value"));
        assertThat(action.info).containsEntry("info", List.of("value"));
        TraceDocument trace = traceDocument(now, message2.id);
        assertThat(trace.content).isEqualTo(message2.traceLog);

        elasticSearch.refreshIndex(indexService.indexName("action", now));

        List<ActionDocument> actions = searchActionDocument(now, "context.key", "value");
        assertThat(actions).hasSize(1);
    }

    @Test
    void indexWithDifferentDateFormatValues() {
        ActionLogMessage message = message("1", "OK");
        // instant.toString() outputs without nano fraction if nano is 0, refer to java.time.format.DateTimeFormatter.ISO_INSTANT
        message.date = ZonedDateTime.now().withNano(0).toInstant();
        actionService.index(List.of(message));

        message.date = ZonedDateTime.now().withNano(123456).toInstant();
        actionService.index(List.of(message));
    }

    @Test
    void bulkIndex() {
        List<ActionLogMessage> messages = Lists.newArrayList();
        for (int i = 0; i < 6; i++) {
            ActionLogMessage message = message("bulk-" + i, "TRACE");
            message.traceLog = "trace";
            messages.add(message);
        }

        LocalDate now = LocalDate.of(2016, Month.JANUARY, 15);
        actionService.index(messages, now);

        ActionDocument action = actionDocument(now, messages.get(0).id);
        assertThat(action.result).isEqualTo("TRACE");
    }

    @Test
    void testActionGroup() {
        ActionLogMessage message = message("1", "OK");
        message.app = "test-service";
        LocalDate now = LocalDate.of(2022, Month.JULY, 26);
        actionService.index(List.of(message), now);
        ActionDocument action = actionDocument(message.app, now, message.id);
        assertThat(action.app).isEqualTo(message.app);

        List<ActionLogMessage> messages = Lists.newArrayList();
        for (int i = 0; i < 6; i++) {
            message = message("bulk-" + i, "OK");
            message.app = "test-service-" + i;
            messages.add(message);
        }
        actionService.index(messages, now);
        ActionLogMessage message0 = messages.get(0);
        ActionDocument action0 = actionDocument(message0.app, now, message0.id);
        assertThat(action0.app).isEqualTo(message0.app);
        ActionLogMessage message2 = messages.get(2);
        ActionDocument action2 = actionDocument(message2.app, now, message2.id);
        assertThat(action2.app).isEqualTo(message2.app);
    }

    private ActionDocument actionDocument(LocalDate now, String id) {
        var request = new GetRequest();
        request.index = indexService.indexName("action", now);
        request.id = id;
        return actionType.get(request).orElseThrow(() -> new Error("not found"));
    }

    private ActionDocument actionDocument(String group, LocalDate now, String id) {
        var request = new GetRequest();
        request.index = indexService.indexName(actionService.actionIndexName(group), now);
        request.id = id;
        return actionType.get(request).orElseThrow(() -> new Error("not found"));
    }

    private List<ActionDocument> searchActionDocument(LocalDate now, String key, String value) {
        var request = new SearchRequest();
        request.query = new Query.Builder().match(match(key, value)).build();
        request.index = indexService.indexName("action", now);
        return actionType.search(request).hits;
    }

    private TraceDocument traceDocument(LocalDate now, String id) {
        var request = new GetRequest();
        request.index = indexService.indexName("trace", now);
        request.id = id;
        return traceType.get(request).orElseThrow(() -> new Error("not found"));
    }

    private ActionLogMessage message(String id, String result) {
        var message = new ActionLogMessage();
        message.id = id;
        message.date = Instant.now();
        message.result = result;
        return message;
    }
}
