package core.framework.search.impl;

import core.framework.internal.json.JSONReader;
import core.framework.internal.json.JSONWriter;
import core.framework.internal.validate.Validator;
import core.framework.log.ActionLogContext;
import core.framework.search.AnalyzeRequest;
import core.framework.search.AnalyzeTokens;
import core.framework.search.BulkIndexWithRoutingRequest;
import core.framework.search.IndexWithRoutingRequest;
import core.framework.search.ScoredSearchResponse;
import core.framework.search.SearchException;
import core.framework.search.SearchRequest;
import core.framework.util.StopWatch;
import core.framework.util.Strings;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.AnalyzeResponse;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static core.framework.log.Markers.errorCode;
import static org.elasticsearch.client.Requests.searchRequest;

/**
 * @author miller
 */
public final class ElasticSearchTypeImplExtension<T> {
    private final Logger logger = LoggerFactory.getLogger(ElasticSearchTypeImplExtension.class);

    private final ElasticSearchImpl elasticSearch;
    private final String index;
    private final long slowOperationThresholdInNanos;
    private final int maxResultWindow;
    private final JSONReader<T> reader;
    private final JSONWriter<T> writer;
    private final Validator<T> validator;

    ElasticSearchTypeImplExtension(String index, long slowOperationThresholdInNanos, JSONReader<T> reader, JSONWriter<T> writer, Validator<T> validator, ElasticSearchImpl elasticSearch) {
        this.elasticSearch = elasticSearch;
        this.maxResultWindow = elasticSearch.maxResultWindow;
        this.index = index;
        this.slowOperationThresholdInNanos = slowOperationThresholdInNanos;
        this.reader = reader;
        this.writer = writer;
        this.validator = validator;
    }

    public ScoredSearchResponse<T> scoredSearch(SearchRequest request) {
        var watch = new StopWatch();
        validate(request);
        long esTook = 0;
        String index = request.index == null ? this.index : request.index;
        int hits = 0;
        try {
            var searchRequest = searchRequest(index);
            if (request.type != null) searchRequest.searchType(request.type);
            SearchSourceBuilder source = searchRequest.source().query(request.query);
            request.aggregations.forEach(source::aggregation);
            request.sorts.forEach(source::sort);
            if (request.skip != null) source.from(request.skip);
            if (request.limit != null) source.size(request.limit);
            if (request.trackTotalHitsUpTo != null) source.trackTotalHitsUpTo(request.trackTotalHitsUpTo);

            org.elasticsearch.action.search.SearchResponse response = search(searchRequest);
            esTook = response.getTook().nanos();
            hits = response.getHits().getHits().length;

            return scoredSearchResponse(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("elasticsearch", elapsed, hits, 0);
            logger.debug("search, hits={}, esTook={}, elapsed={}", hits, esTook, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    public void indexWithRouting(IndexWithRoutingRequest<T> request) {
        var watch = new StopWatch();
        String index = request.index == null ? this.index : request.index;
        validator.validate(request.source, false);
        byte[] document = writer.toJSON(request.source);
        try {
            var indexRequest = new org.elasticsearch.action.index.IndexRequest(index).routing(request.routing).id(request.id).source(document, XContentType.JSON);
            elasticSearch.client().index(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("elasticsearch", elapsed, 0, 1);
            logger.debug("index, index={}, id={}, routing={}, elapsed={}", index, request.id, request.routing, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    public void bulkIndexWithRouting(BulkIndexWithRoutingRequest<T> request) {
        var watch = new StopWatch();
        if (request.requests == null || request.requests.isEmpty()) throw new Error("request.sources must not be empty");
        String index = request.index == null ? this.index : request.index;
        var bulkRequest = new BulkRequest();
        for (Map.Entry<String, BulkIndexWithRoutingRequest.Request<T>> entry : request.requests.entrySet()) {
            String id = entry.getKey();
            T source = entry.getValue().source;
            String routing = entry.getValue().routing;
            validator.validate(source, false);
            var indexRequest = new org.elasticsearch.action.index.IndexRequest(index).id(id).routing(routing).source(writer.toJSON(source), XContentType.JSON);
            bulkRequest.add(indexRequest);
        }
        long esTook = 0;
        try {
            BulkResponse response = elasticSearch.client().bulk(bulkRequest, RequestOptions.DEFAULT);
            esTook = response.getTook().nanos();
            if (response.hasFailures()) throw new SearchException(response.buildFailureMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("elasticsearch", elapsed, 0, request.requests.size());
            logger.debug("bulkIndex, index={}, size={}, esTook={}, elapsed={}", index, request.requests.size(), esTook, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    public AnalyzeTokens detailedAnalyze(AnalyzeRequest request) {
        var watch = new StopWatch();
        String index = request.index == null ? this.index : request.index;
        try {
            var analyzeRequest = org.elasticsearch.client.indices.AnalyzeRequest.withIndexAnalyzer(index, request.analyzer, request.text);
            AnalyzeResponse response = elasticSearch.client().indices().analyze(analyzeRequest, RequestOptions.DEFAULT);
            var analyzeTokens = new AnalyzeTokens();
            analyzeTokens.tokens = response.getTokens().stream().map(this::token).collect(Collectors.toList());
            return analyzeTokens;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            long elapsed = watch.elapsed();
            ActionLogContext.track("elasticsearch", elapsed);
            logger.debug("analyze, index={}, analyzer={}, elapsed={}", index, request.analyzer, elapsed);
            checkSlowOperation(elapsed);
        }
    }

    private ScoredSearchResponse<T> scoredSearchResponse(org.elasticsearch.action.search.SearchResponse response) throws IOException {
        SearchHit[] hits = response.getHits().getHits();
        List<ScoredSearchResponse.ScoredHit<T>> items = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            var scoredHit = new ScoredSearchResponse.ScoredHit<T>();
            scoredHit.source = reader.fromJSON(BytesReference.toBytes(hit.getSourceRef()));
            float score = hit.getScore();
            scoredHit.score = Float.isNaN(score) ? null : score;
            items.add(scoredHit);
        }
        Aggregations aggregationResponse = response.getAggregations();
        Map<String, Aggregation> aggregations = aggregationResponse == null ? Map.of() : aggregationResponse.asMap();
        TotalHits totalHits = response.getHits().getTotalHits();
        long total = totalHits == null ? -1 : totalHits.value;
        return new ScoredSearchResponse<>(items, total, aggregations);
    }

    private org.elasticsearch.action.search.SearchResponse search(org.elasticsearch.action.search.SearchRequest searchRequest) throws IOException {
        logger.debug("search, request={}", searchRequest);
        org.elasticsearch.action.search.SearchResponse response = elasticSearch.client().search(searchRequest, RequestOptions.DEFAULT);
        if (response.getFailedShards() > 0) logger.warn("elasticsearch shards failed, response={}", response);
        return response;
    }

    private void validate(SearchRequest request) {
        int skip = request.skip == null ? 0 : request.skip;
        int limit = request.limit == null ? 0 : request.limit;
        if (skip + limit > maxResultWindow)
            throw new Error(Strings.format("result window is too large, skip + limit must be less than or equal to max result window, skip={}, limit={}, maxResultWindow={}", request.skip, request.limit, maxResultWindow));
    }

    private void checkSlowOperation(long elapsed) {
        if (elapsed > slowOperationThresholdInNanos) {
            logger.warn(errorCode("SLOW_ES"), "slow elasticsearch operation, elapsed={}", Duration.ofNanos(elapsed));
        }
    }

    private AnalyzeTokens.Token token(AnalyzeResponse.AnalyzeToken analyzeToken) {
        var token = new AnalyzeTokens.Token();
        token.term = analyzeToken.getTerm();
        token.startOffset = analyzeToken.getStartOffset();
        token.endOffset = analyzeToken.getEndOffset();
        token.type = analyzeToken.getType();
        token.position = analyzeToken.getPosition();
        return token;
    }
}
