package core.framework.search;


import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;

import java.util.List;
import java.util.Map;

/**
 * @author miller
 */
public class ScoredSearchResponse<T> {
    public final List<ScoredHit<T>> hits;
    public final long totalHits;
    public final Map<String, Aggregate> aggregations;

    public ScoredSearchResponse(List<ScoredHit<T>> hits, long totalHits, Map<String, Aggregate> aggregations) {
        this.hits = hits;
        this.totalHits = totalHits;
        this.aggregations = aggregations;
    }

    public static class ScoredHit<T> {
        public T source;
        public Double score;
    }
}
