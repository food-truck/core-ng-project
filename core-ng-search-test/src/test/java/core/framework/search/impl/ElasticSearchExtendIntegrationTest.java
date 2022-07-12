package core.framework.search.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldAndFormat;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.InnerHits;
import co.elastic.clients.elasticsearch.core.search.Rescore;
import co.elastic.clients.elasticsearch.core.search.RescoreQuery;
import co.elastic.clients.elasticsearch.core.search.ScoreMode;
import core.framework.inject.Inject;
import core.framework.json.JSON;
import core.framework.search.ElasticSearch;
import core.framework.search.ElasticSearchType;
import core.framework.search.ExtendSearchRequest;
import core.framework.search.ExtendSearchResponse;
import core.framework.search.Index;
import core.framework.search.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static core.framework.search.query.Queries.match;
import static core.framework.search.query.Queries.term;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author miller
 */
class ElasticSearchExtendIntegrationTest extends IntegrationTest {
    private static final String DOCUMENT_INDEX = TestDocument.class.getAnnotation(Index.class).name();
    @Inject
    ElasticSearch elasticSearch;
    @Inject
    ElasticSearchType<TestDocument> documentType;

    @AfterEach
    void cleanup() {
        documentType.bulkDelete(range(0, 100).mapToObj(String::valueOf).toList());
        elasticSearch.refreshIndex(DOCUMENT_INDEX);
    }

    @Test
    void extendSearch() {
        TestDocument document = document("1", "1st Test's Product", 1, 0, null, LocalTime.NOON);
        documentType.index(document.id, document);
        elasticSearch.refreshIndex(DOCUMENT_INDEX);

        Query query = new Query.Builder().term(term("id", "1")).build();
        RescoreQuery rescoreQuery = new RescoreQuery.Builder().query(query).queryWeight(0.7d).rescoreQueryWeight(1.2d).scoreMode(ScoreMode.Max).build();
        Rescore rescore = new Rescore.Builder().query(rescoreQuery).windowSize(5).build();

        var request = new ExtendSearchRequest();
        request.skip = 0;
        request.limit = 10;
        request.rescores = List.of(
            rescore
        );
        ExtendSearchResponse<TestDocument> response = documentType.extendSearch(request);
        assertEquals(1, response.totalHits);
    }

    @Test
    void extendSearchNestedAndInnerHits() {
        TestDocument document = document("1", "1st Test's Product", 1, 0, null, LocalTime.NOON);
        document.nested = List.of(nested("field1", "field2"), nested("field3 word1", "field4 word2"));
        documentType.index(document.id, document);
        elasticSearch.refreshIndex(DOCUMENT_INDEX);

        Query termQuery = new Query.Builder().term(new TermQuery.Builder().field("nested.field1").value("field3").build()).build();
        InnerHits nestedInnerHit = new InnerHits.Builder().name("nestedInnerHit").docvalueFields(new FieldAndFormat.Builder().field("nested.field1.keyword").build()).build();
        NestedQuery nestedQuery = new NestedQuery.Builder().path("nested").query(termQuery).scoreMode(ChildScoreMode.None).innerHits(nestedInnerHit).build();
        Query query = new Query.Builder().nested(nestedQuery).build();

        var request = new ExtendSearchRequest();
        request.query = query;
        request.skip = 0;
        request.limit = 10;
        ExtendSearchResponse<TestDocument> response = documentType.extendSearch(request);
        assertEquals(1, response.totalHits);
        assertEquals(1, response.hits.get(0).innerHits.size());
    }

    @Test
    void extendSearchHighlights() {
        TestDocument document = document("1", "1st Test's Product", 1, 0, null, LocalTime.NOON);
        documentType.index(document.id, document);
        elasticSearch.refreshIndex("document");

        var request = new ExtendSearchRequest();
        request.query = new Query.Builder().bool(b -> b.must(m -> m.match(match("string_field", "first")))
            .filter(f -> f.term(term("enum_field", JSON.toEnumValue(TestDocument.TestEnum.VALUE1))))).build();
        request.highlight = new Highlight.Builder().fields(Map.of("string_field", new HighlightField.Builder().build())).build();

        ExtendSearchResponse<TestDocument> response = documentType.extendSearch(request);
        assertEquals(1, response.totalHits);
        assertThat(response.hits).hasSize(1)
            .first().matches(hit -> hit.highlightFields.containsKey("string_field"));
    }

    private TestDocument document(String id, String stringField, int intField, double doubleField, ZonedDateTime dateTimeField, LocalTime timeField) {
        var document = new TestDocument();
        document.id = id;
        document.stringField = stringField;
        document.intField = intField;
        document.doubleField = doubleField;
        document.zonedDateTimeField = dateTimeField;
        document.localTimeField = timeField;
        document.enumField = TestDocument.TestEnum.VALUE1;
        document.completion1 = stringField + "-Complete1";
        document.completion2 = stringField + "-Complete2";
        return document;
    }

    private TestDocument.Nested nested(String field1, String field2) {
        TestDocument.Nested nested = new TestDocument.Nested();
        nested.field1 = field1;
        nested.field2 = field2;
        return nested;
    }
}
