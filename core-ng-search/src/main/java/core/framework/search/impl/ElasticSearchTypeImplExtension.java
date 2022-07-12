package core.framework.search.impl;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import core.framework.internal.asm.CodeBuilder;
import core.framework.internal.validate.Validator;
import core.framework.log.ActionLogContext;
import core.framework.search.AnalyzeRequest;
import core.framework.search.AnalyzeTokens;
import core.framework.search.BulkIndexWithRoutingRequest;
import core.framework.search.ExtendSearchRequest;
import core.framework.search.ExtendSearchResponse;
import core.framework.search.IndexWithRoutingRequest;
import core.framework.search.SearchException;
import core.framework.util.StopWatch;
import core.framework.util.Strings;
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

/**
 * @author miller
 */
public final class ElasticSearchTypeImplExtension<T> {
    private final Logger logger = LoggerFactory.getLogger(ElasticSearchTypeImplExtension.class);

    private final ElasticSearchImpl elasticSearch;
    private final String index;
    private final long slowOperationThresholdInNanos;
    private final int maxResultWindow;
    private final Validator<T> validator;

    private final Class<T> documentClass;

    ElasticSearchTypeImplExtension(String index, long slowOperationThresholdInNanos, Validator<T> validator, ElasticSearchImpl elasticSearch, Class<T> documentClass) {
        this.elasticSearch = elasticSearch;
        this.maxResultWindow = elasticSearch.maxResultWindow;
        this.index = index;
        this.slowOperationThresholdInNanos = slowOperationThresholdInNanos;
        this.validator = validator;
        this.documentClass = documentClass;
    }

    public ExtendSearchResponse<T> extendSearch(ExtendSearchRequest request) {
        var watch = new StopWatch();
        validate(request);
        long esTook = 0;
        String index = request.index == null ? this.index : request.index;
        int hits = 0;
        try {
            var searchRequest = co.elastic.clients.elasticsearch.core.SearchRequest.of(builder -> {
                builder.index(index).query(request.query).aggregations(request.aggregations).sort(request.sorts)
                    .searchType(request.type).highlight(request.highlight).rescore(request.rescores)
                    .from(request.skip).size(request.limit).timeout(elasticSearch.timeout.toMillis() + "ms");
                if (request.trackTotalHitsUpTo != null) builder.trackTotalHits(t -> t.count(request.trackTotalHitsUpTo));
                return builder;
            });
            var response = elasticSearch.client.search(searchRequest, documentClass);
            esTook = response.took() * 1_000_000;
            hits = response.hits().hits().size();

            return extendSearchResponse(response);
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
        try {
            elasticSearch.client.index(builder -> builder.index(index).routing(request.routing).id(request.id).document(request.source));
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
        var operations = new ArrayList<BulkOperation>(request.requests.size());
        for (Map.Entry<String, BulkIndexWithRoutingRequest.Request<T>> entry : request.requests.entrySet()) {
            String id = entry.getKey();
            T source = entry.getValue().source;
            String routing = entry.getValue().routing;
            validator.validate(source, false);
            operations.add(BulkOperation.of(builder -> builder.index(i -> i.index(index).id(id).routing(routing).document(source))));
        }
        long esTook = 0;
        try {
            BulkResponse response = elasticSearch.client.bulk(builder -> builder.operations(operations));
            esTook = response.took() * 1_000_000;
            validate(response);
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
            AnalyzeResponse response = elasticSearch.client.indices().analyze(builder ->
                builder.index(index).analyzer(request.analyzer).text(request.text));
            var analyzeTokens = new AnalyzeTokens();
            analyzeTokens.tokens = response.tokens().stream().map(this::token).collect(Collectors.toList());
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

    private ExtendSearchResponse<T> extendSearchResponse(SearchResponse<T> response) throws IOException {
        List<ExtendSearchResponse.ExtendHit<T>> items = response.hits().hits().stream().map(hit -> {
            var extendHit = new ExtendSearchResponse.ExtendHit<T>();
            extendHit.source = hit.source();
            Double score = hit.score();
            extendHit.score = score.isNaN() ? null : score;
            extendHit.matchedQueries = hit.matchedQueries();
            extendHit.innerHits = hit.innerHits();
            extendHit.highlightFields = hit.highlight();
            return extendHit;
        }).collect(Collectors.toList());
        return new ExtendSearchResponse<>(items, response.hits().total() == null ? 0 : response.hits().total().value(), response.aggregations());
    }

    private void validate(ExtendSearchRequest request) {
        int skip = request.skip == null ? 0 : request.skip;
        int limit = request.limit == null ? 0 : request.limit;
        if (skip + limit > maxResultWindow)
            throw new Error(Strings.format("result window is too large, skip + limit must be less than or equal to max result window, skip={}, limit={}, maxResultWindow={}", request.skip, request.limit, maxResultWindow));
    }

    private void validate(BulkResponse response) {
        if (!response.errors()) return;

        var builder = new CodeBuilder();
        builder.append("bulk operation failed, errors=[\n");
        for (BulkResponseItem item : response.items()) {
            ErrorCause error = item.error();
            if (error != null) {
                builder.append("id={}, error={}, stackTrace={}\n", item.id(), error.reason(), error.stackTrace());
            }
        }
        builder.append("]");
        throw new SearchException(builder.build());
    }

    private void checkSlowOperation(long elapsed) {
        if (elapsed > slowOperationThresholdInNanos) {
            logger.warn(errorCode("SLOW_ES"), "slow elasticsearch operation, elapsed={}", Duration.ofNanos(elapsed));
        }
    }

    private AnalyzeTokens.Token token(AnalyzeToken analyzeToken) {
        var token = new AnalyzeTokens.Token();
        token.term = analyzeToken.token();
        token.startOffset = analyzeToken.startOffset();
        token.endOffset = analyzeToken.endOffset();
        token.type = analyzeToken.type();
        token.position = analyzeToken.position();
        return token;
    }
}
