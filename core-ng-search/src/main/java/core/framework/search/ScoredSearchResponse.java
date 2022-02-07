package core.framework.search;

import org.elasticsearch.search.aggregations.Aggregation;

import java.util.List;
import java.util.Map;

/**
 * @author miller
 */
public class ScoredSearchResponse<T> {
    public final List<ScoredHit<T>> hits;
    public final long totalHits;
    public final Map<String, Aggregation> aggregations;

    public ScoredSearchResponse(List<ScoredHit<T>> hits, long totalHits, Map<String, Aggregation> aggregations) {
        this.hits = hits;
        this.totalHits = totalHits;
        this.aggregations = aggregations;
    }

    public static class ScoredHit<T> {
        public T source;
        public Float score;
    }
}
