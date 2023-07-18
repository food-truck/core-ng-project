package core.log.service;

import core.framework.internal.validate.ValidationException;
import core.framework.json.JSON;
import core.framework.log.Markers;
import core.framework.util.Maps;
import core.log.LogGroupConfig;
import core.log.LogIndexRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * @author rickeyhong
 */
public class AppGroupMappingLogIndexRouter implements LogIndexRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppGroupMappingLogIndexRouter.class);
    private final String mainIndexName;
    private final Map<String, String> appGroupMapping = Maps.newHashMap();

    public AppGroupMappingLogIndexRouter(String mainIndexName, String appLogGroupMapping) {
        this.mainIndexName = mainIndexName;
        var logGroupConfig = JSON.fromJSON(LogGroupConfig.class, appLogGroupMapping);
        if (logGroupConfig.groups == null) {
            throw new ValidationException(Collections.singletonMap("groups", "field must not be null"));
        }
        logGroupConfig.groups.forEach((group, apps) -> apps.forEach(app -> {
            var previous = appGroupMapping.put(app, group);
            if (previous != null) {
                LOGGER.warn(Markers.errorCode("DUPLICATED_APP_DETECTED"), "duplicated app {} detected, current group {}, previous group {}", app, group, previous);
            }
        }));
    }
    
    @Override
    public String route(String app) {
        return Optional.ofNullable(appGroupMapping.get(app)).map(group -> mainIndexName + "-" + group).orElse(mainIndexName);
    }
}