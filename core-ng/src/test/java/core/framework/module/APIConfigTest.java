package core.framework.module;

import core.framework.api.web.service.PUT;
import core.framework.api.web.service.Path;
import core.framework.api.web.service.PathParam;
import core.framework.internal.module.ModuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author neo
 */
class APIConfigTest {
    private APIConfig config;

    @BeforeEach
    void createAPIConfig() {
        config = new APIConfig();
        config.initialize(new ModuleContext(null), null);
    }

    @Test
    void service() {
        config.service(TestWebService.class, new TestWebServiceImpl());

        assertThat(config.context.apiController.serviceInterfaces).contains(TestWebService.class);
    }

    @Test
    void client() {
        config.httpClient().timeout(Duration.ofSeconds(5));
        config.client(TestWebService.class, "http://localhost");

        TestWebService client = (TestWebService) config.context.beanFactory.bean(TestWebService.class, null);
        assertThat(client).isNotNull();
    }

    public interface TestWebService {
        @PUT
        @Path("/test/:id")
        void put(@PathParam("id") Integer id);
    }

    public static class TestWebServiceImpl implements TestWebService {
        @Override
        public void put(Integer id) {
        }
    }
}
