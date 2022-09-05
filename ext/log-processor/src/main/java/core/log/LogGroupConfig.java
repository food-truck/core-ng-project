package core.log;

import core.framework.api.json.Property;
import core.framework.api.validate.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author miller
 */
public class LogGroupConfig {
    @NotNull
    @Property(name = "groups")
    public Map<String, List<String>> groups;
}

/* example
{
    "groups": {
        "consumer": [
            "order-service",
            "payment-service"
        ],
        "fleet": [
            "truck-service",
            "truck-alerting-service"
        ]
    }
}
 */
