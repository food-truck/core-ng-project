package core.framework.search;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author neo
 */
public interface ElasticSearchType<T> {
    SearchResponse<T> search(SearchRequest request);

    ScoredSearchResponse<T> scoredSearch(SearchRequest request);

    List<String> complete(CompleteRequest request);

    default List<String> complete(String prefix, String... fields) {
        CompleteRequest request = new CompleteRequest();
        request.prefix = prefix;
        request.fields = List.of(fields);
        return complete(request);
    }

    Optional<T> get(GetRequest request);

    default Optional<T> get(String id) {
        var request = new GetRequest();
        request.id = id;
        return get(request);
    }

    void index(IndexRequest<T> request);

    default void index(String id, T source) {
        var request = new IndexRequest<T>();
        request.id = id;
        request.source = source;
        index(request);
    }

    void indexWithRouting(IndexWithRoutingRequest<T> request);

    default void indexWithRouting(String id, T source, String routing) {
        var request = new IndexWithRoutingRequest<T>();
        request.id = id;
        request.source = source;
        request.routing = routing;
        indexWithRouting(request);
    }

    void bulkIndex(BulkIndexRequest<T> request);

    default void bulkIndex(Map<String, T> sources) {
        var request = new BulkIndexRequest<T>();
        request.sources = sources;
        bulkIndex(request);
    }

    void bulkIndexWithRouting(BulkIndexWithRoutingRequest<T> request);

    default void bulkIndexWithRouting(Map<String, BulkIndexWithRoutingRequest.Request<T>> requests) {
        var request = new BulkIndexWithRoutingRequest<T>();
        request.requests = requests;
        bulkIndexWithRouting(request);
    }

    void update(UpdateRequest<T> request);

    default void update(String id, String script, @Nullable Map<String, Object> params) {
        var request = new UpdateRequest<T>();
        request.id = id;
        request.script = script;
        request.params = params;
        update(request);
    }

    boolean delete(DeleteRequest request);

    default boolean delete(String id) {
        var request = new DeleteRequest();
        request.id = id;
        return delete(request);
    }

    void bulkDelete(BulkDeleteRequest request);

    default void bulkDelete(List<String> ids) {
        var request = new BulkDeleteRequest();
        request.ids = ids;
        bulkDelete(request);
    }

    long deleteByQuery(DeleteByQueryRequest request);

    List<String> analyze(AnalyzeRequest request);   // can be used to test customized analyzer

    default List<String> analyze(String analyzer, String text) {
        AnalyzeRequest request = new AnalyzeRequest();
        request.analyzer = analyzer;
        request.text = text;
        return analyze(request);
    }

    AnalyzeTokens detailedAnalyze(AnalyzeRequest request);

    default AnalyzeTokens detailedAnalyze(String analyzer, String text) {
        AnalyzeRequest request = new AnalyzeRequest();
        request.analyzer = analyzer;
        request.text = text;
        return detailedAnalyze(request);
    }

    void forEach(ForEach<T> forEach);
}
