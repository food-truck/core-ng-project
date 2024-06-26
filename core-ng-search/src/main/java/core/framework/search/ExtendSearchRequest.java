package core.framework.search;

import co.elastic.clients.elasticsearch._types.SearchType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Rescore;
import core.framework.util.Lists;
import core.framework.util.Maps;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * @author miller
 */
public class ExtendSearchRequest {
    public final Map<String, Aggregation> aggregations = Maps.newHashMap();
    public final List<SortOptions> sorts = Lists.newArrayList();
    @Nullable
    public String index;
    public Query query;
    public Highlight highlight;
    public List<Rescore> rescores = Lists.newArrayList();
    @Nullable
    public SearchType type;
    @Nullable
    public Integer skip;
    @Nullable
    public Integer limit;
    @Nullable
    public Integer trackTotalHitsUpTo;

    public void trackTotalHits() {
        trackTotalHitsUpTo = Integer.MAX_VALUE;
    }
}
