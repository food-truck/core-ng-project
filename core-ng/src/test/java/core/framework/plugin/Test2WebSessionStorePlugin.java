package core.framework.plugin;

import core.framework.internal.web.session.LocalSessionStore;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * @author rickeyhong 
 */
public class Test2WebSessionStorePlugin implements WebSessionStorePlugin {
    private final LocalSessionStore localSessionStore = new LocalSessionStore();
    
    @Override
    public Map<String, String> get(String sessionId, String domain) {
        return localSessionStore.getAndRefresh(sessionId, domain, Duration.ofMinutes(30));
    }
    
    @Override
    public void refresh(String sessionId, String domain, Duration timeout) {
        localSessionStore.getAndRefresh(sessionId, domain, timeout);
    }

    @Override
    public void save(String sessionId, String domain, Map<String, String> values, Set<String> changedFields, Duration timeout) {
        localSessionStore.save(sessionId, domain, values, changedFields, timeout);
    }

    @Override
    public void invalidate(String sessionId, String domain) {
        localSessionStore.invalidate(sessionId, domain);
    }

    @Override
    public void invalidateByKey(String key, String value) {
        localSessionStore.invalidateByKey(key, value);
    }

    @Override
    public void cleanup() {
        localSessionStore.cleanup();
    }
}