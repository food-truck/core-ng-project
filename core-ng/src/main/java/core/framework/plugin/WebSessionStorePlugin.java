package core.framework.plugin;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * @author rickeyhong 
 */
public non-sealed interface WebSessionStorePlugin extends Plugin {
    Map<String, String> get(String sessionId, String domain);
    
    void refresh(String sessionId, String domain, Duration timeout);

    void save(String sessionId, String domain, Map<String, String> values, Set<String> changedFields, Duration timeout);

    void invalidate(String sessionId, String domain);

    void invalidateByKey(String key, String value);

    void cleanup();
}
