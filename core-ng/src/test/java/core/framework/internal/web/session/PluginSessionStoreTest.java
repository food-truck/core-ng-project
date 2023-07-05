package core.framework.internal.web.session;

import core.framework.internal.log.LogManager;
import core.framework.internal.module.ModuleContext;
import core.framework.plugin.Test1WebSessionStorePlugin;
import core.framework.plugin.Test2WebSessionStorePlugin;
import core.framework.plugin.WebSessionStorePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author neo
 */
class PluginSessionStoreTest {
    private PluginSessionStore pluginSessionStore;

    @BeforeEach
    void createPluginSessionStore() {
        var context = new ModuleContext(new LogManager());
        context.pluginManager.register(WebSessionStorePlugin.class, new Test1WebSessionStorePlugin());
        context.pluginManager.register(WebSessionStorePlugin.class, new Test2WebSessionStorePlugin());
        
        pluginSessionStore = new PluginSessionStore();
        pluginSessionStore.initialize(context);
        pluginSessionStore.save("sessionId", null, Map.of("key", "value"), Set.of(), Duration.ofSeconds(30));
    }

    @Test
    void getAndRefresh() {
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNotNull();
    }

    @Test
    void getAndRefreshWithThrow() {
        var context = new ModuleContext(new LogManager());
        var test1Plugin = spy(Test1WebSessionStorePlugin.class);
        context.pluginManager.register(WebSessionStorePlugin.class, test1Plugin);
        var test2Plugin = spy(Test2WebSessionStorePlugin.class);
        context.pluginManager.register(WebSessionStorePlugin.class, test2Plugin);
        
        pluginSessionStore = new PluginSessionStore();
        pluginSessionStore.initialize(context);
        pluginSessionStore.save("sessionId", null, Map.of("key", "value"), Set.of(), Duration.ofSeconds(30));

        when(test1Plugin.get(anyString(), any())).thenThrow(new RuntimeException("mock"));
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNotNull();

        when(test2Plugin.get(anyString(), any())).thenThrow(new RuntimeException("mock"));
        assertThatThrownBy(() -> pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isInstanceOf(RuntimeException.class).hasMessage("mock");
    }

    @Test
    void getAndRefreshWithExpiredSession() {
        pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofMillis(0));
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNull();
    }

    @Test
    void getAndRefreshWithNotExistedSessionId() {
        Map<String, String> values = pluginSessionStore.getAndRefresh("sessionId2", null, Duration.ofSeconds(30));
        assertThat(values).isNull();
    }

    @Test
    void save() {
        pluginSessionStore.save("sessionId2", null, Map.of("key", "value"), Set.of(), Duration.ofSeconds(30));
        var session = pluginSessionStore.getAndRefresh("sessionId2", null, Duration.ofSeconds(30));
        assertThat(session).hasSize(1);
    }

    @Test
    void invalidate() {
        pluginSessionStore.invalidate("sessionId", null);
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNull();
    }

    @Test
    void invalidateByKey() {
        pluginSessionStore.save("sessionId1", null, Map.of("key", "v1"), Set.of(), Duration.ofSeconds(30));
        pluginSessionStore.save("sessionId2", null, Map.of("key", "v1"), Set.of(), Duration.ofSeconds(30));
        pluginSessionStore.save("sessionId3", null, Map.of("key", "v2"), Set.of(), Duration.ofSeconds(30));

        pluginSessionStore.invalidateByKey("key", "v1");
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNotNull();
        assertThat(pluginSessionStore.getAndRefresh("sessionId1", null, Duration.ofSeconds(30))).isNull();
        assertThat(pluginSessionStore.getAndRefresh("sessionId2", null, Duration.ofSeconds(30))).isNull();
        assertThat(pluginSessionStore.getAndRefresh("sessionId3", null, Duration.ofSeconds(30))).isNotNull();
    }

    @Test
    void cleanup() {
        pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofMillis(0));
        pluginSessionStore.cleanup();
        assertThat(pluginSessionStore.getAndRefresh("sessionId", null, Duration.ofSeconds(30))).isNull();
    }
}