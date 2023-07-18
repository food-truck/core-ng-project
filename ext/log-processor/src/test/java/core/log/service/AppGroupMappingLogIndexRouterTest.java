package core.log.service;

import core.log.LogIndexRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author rickeyhong
 */
class AppGroupMappingLogIndexRouterTest {
    private static LogIndexRouter logIndexRouter;
    
    @BeforeAll
    static void init() {
        logIndexRouter = new AppGroupMappingLogIndexRouter("action", "{\"groups\": {\"platform-services\": [\"identity-service\"]}}");
    }

    @Test
    void route() {
        Assertions.assertEquals(logIndexRouter.route("action"), "action");
        Assertions.assertEquals(logIndexRouter.route("identity-service"), "action-platform-services");
        Assertions.assertEquals(logIndexRouter.route("api-hub"), "action");
    }
}