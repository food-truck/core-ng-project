package core.framework.internal.web;

import core.framework.internal.log.ActionLog;
import core.framework.internal.log.Trace;
import io.undertow.util.HeaderMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author neo
 */
class HTTPHandlerTest {
    private HTTPHandler handler;

    @BeforeEach
    void createHTTPServerHandler() {
        handler = new HTTPHandler(null, null, null);
    }

    @Test
    void linkContext() {
        var actionLog = new ActionLog(null, null);
        var headers = new HeaderMap();
        headers.put(HTTPHandler.HEADER_TRACE, "true");
        headers.put(HTTPHandler.HEADER_CLIENT, "client");
        handler.linkContext(actionLog, headers);

        assertThat(actionLog.trace).isEqualTo(Trace.CURRENT);
        assertThat(actionLog.clients).containsExactly("client");
    }

    @Test
    void maxProcessTime() {
        assertThat(handler.maxProcessTime("invalid")).isEqualTo(handler.maxProcessTimeInNano);
        assertThat(handler.maxProcessTime("100")).isEqualTo(100);
    }
}
