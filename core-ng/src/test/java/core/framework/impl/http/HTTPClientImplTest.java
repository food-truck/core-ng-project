package core.framework.impl.http;

import core.framework.api.http.HTTPStatus;
import core.framework.http.ContentType;
import core.framework.http.HTTPClientException;
import core.framework.http.HTTPHeaders;
import core.framework.http.HTTPMethod;
import core.framework.http.HTTPRequest;
import core.framework.http.HTTPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author neo
 */
class HTTPClientImplTest {
    private HTTPClientImpl httpClient;

    @BeforeEach
    void createHTTPClient() {
        httpClient = new HTTPClientImpl(null, "TestUserAgent", Duration.ofSeconds(10), Duration.ZERO);
    }

    @Test
    void parseHTTPStatus() {
        assertThat(HTTPClientImpl.parseHTTPStatus(200)).isEqualTo(HTTPStatus.OK);
    }

    @Test
    void parseUnsupportedHTTPStatus() {
        assertThatThrownBy(() -> HTTPClientImpl.parseHTTPStatus(525))
                .isInstanceOf(HTTPClientException.class);
    }

    @Test
    void httpRequest() {
        var request = new HTTPRequest(HTTPMethod.POST, "http://localhost/uri");
        request.param("query", "value");
        request.accept(ContentType.APPLICATION_JSON);
        request.body("text", ContentType.TEXT_PLAIN);

        HttpRequest httpRequest = httpClient.httpRequest(request);
        assertThat(httpRequest.uri().toString()).isEqualTo("http://localhost/uri?query=value");
        assertThat(httpRequest.headers().firstValue(HTTPHeaders.ACCEPT)).get().isEqualTo(ContentType.APPLICATION_JSON.toString());
        assertThat(httpRequest.headers().firstValue(HTTPHeaders.USER_AGENT)).get().isEqualTo("TestUserAgent");
    }

    @Test
    void response() {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of("content-type", List.of("text/html"), ":status", List.of("200")), (name, value) -> true));

        HTTPResponse response = httpClient.response(httpResponse);
        assertThat(response.status()).isEqualTo(HTTPStatus.OK);
        assertThat(response.header(HTTPHeaders.CONTENT_TYPE)).as("header should be case insensitive").get().isEqualTo("text/html");
        assertThat(response.contentType()).get().satisfies(contentType -> assertThat(contentType.mediaType()).isEqualTo(ContentType.TEXT_HTML.mediaType()));
        assertThat(response.header(":status")).isEmpty();
    }
}
