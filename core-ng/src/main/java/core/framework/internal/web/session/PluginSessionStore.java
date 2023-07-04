package core.framework.internal.web.session;

import core.framework.internal.module.ModuleContext;
import core.framework.plugin.PluginInitializable;
import core.framework.plugin.WebSessionStorePlugin;
import core.framework.util.Lists;
import core.framework.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author rickeyhong
 */
public final class PluginSessionStore implements SessionStore, PluginInitializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginSessionStore.class);
    private List<WebSessionStorePlugin> plugins;

    @Override
    public boolean isAvailable() {
        return plugins != null;
    }

    @Override
    public void initialize(ModuleContext context) {
        this.plugins = Lists.newArrayList();
        this.plugins.addAll(context.pluginManager.getGroupPlugins(WebSessionStorePlugin.class));
    }

    @Override
    public Map<String, String> getAndRefresh(String sessionId, String domain, Duration timeout) {
        check();
        var iterator = plugins.iterator();
        Map<String, String> session = null;
        while (iterator.hasNext()) {
            var plugin = iterator.next();
            try {
                if (session == null) {
                    session = plugin.get(sessionId, domain);
                }
                plugin.refresh(sessionId, domain, timeout);
            } catch (Exception e) {
                if (!iterator.hasNext()) {
                    throw e;
                }
                LOGGER.warn(Strings.format("getAndRefresh failure! plugin name: {}", plugin.pluginName()), e);
            }
        }
        return session;
    }

    @Override
    public void save(String sessionId, String domain, Map<String, String> values, Set<String> changedFields, Duration timeout) {
        eachRun("save", plugin -> plugin.save(sessionId, domain, values, changedFields, timeout));
    }

    @Override
    public void invalidate(String sessionId, String domain) {
        eachRun("invalidate", plugin -> plugin.invalidate(sessionId, domain));
    }

    @Override
    public void invalidateByKey(String key, String value) {
        eachRun("invalidateByKey", plugin -> plugin.invalidateByKey(key, value));
    }

    public void cleanup() {
        eachRun("cleanup", WebSessionStorePlugin::cleanup);
    }

    private void eachRun(String method, Consumer<WebSessionStorePlugin> consumer) {
        check();
        var iterator = plugins.iterator();
        while (iterator.hasNext()) {
            var plugin = iterator.next();
            try {
                consumer.accept(plugin);
            } catch (Exception e) {
                if (!iterator.hasNext()) {
                    throw e;
                }
                LOGGER.warn(Strings.format("{} failure! plugin name: {}", method, plugin.pluginName()), e);
            }
        }
    }

    private void check() {
        if (!isAvailable()) {
            throw new IllegalStateException("Not Availiable!");
        } else if (plugins.isEmpty()) {
            throw new IllegalStateException("Not Found Plugin!");
        }
    }
}