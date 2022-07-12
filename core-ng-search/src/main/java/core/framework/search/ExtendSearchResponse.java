package core.framework.search;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.search.InnerHitsResult;

import java.util.List;
import java.util.Map;

/**
 * @author miller
 */
public class ExtendSearchResponse<T> {
    public final List<ExtendHit<T>> hits;
    public final long totalHits;
    public final Map<String, Aggregate> aggregations;

    public ExtendSearchResponse(List<ExtendHit<T>> hits, long totalHits, Map<String, Aggregate> aggregations) {
        this.hits = hits;
        this.totalHits = totalHits;
        this.aggregations = aggregations;
    }

    public static class ExtendHit<T> {
        public T source;
        public Double score;
        public List<String> matchedQueries;
        public Map<String, InnerHitsResult> innerHits;
        public Map<String, List<String>> highlightFields;
    }
}
