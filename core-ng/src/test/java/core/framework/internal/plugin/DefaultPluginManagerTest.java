package core.framework.internal.plugin;

import core.framework.plugin.Test1WebSessionStorePlugin;
import core.framework.plugin.Test2WebSessionStorePlugin;
import core.framework.plugin.WebSessionStorePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author rickeyhong 
 */
class DefaultPluginManagerTest {
    private DefaultPluginManager pluginManager;
    
    @BeforeEach
    void createDefaultPluginManagerTest() {
        this.pluginManager = new DefaultPluginManager();
    }

    @Test
    void register() {
        pluginManager.register(WebSessionStorePlugin.class, new Test1WebSessionStorePlugin());
        assertThat(pluginManager.getGroupPlugins(WebSessionStorePlugin.class)).hasSize(1);
    }

    @Test
    void remove() {
        var plugin1 = new Test1WebSessionStorePlugin();
        pluginManager.register(WebSessionStorePlugin.class, plugin1);
        var plugin2 = new Test2WebSessionStorePlugin();
        pluginManager.register(WebSessionStorePlugin.class, plugin2);
        assertThat(pluginManager.getGroupPlugins(WebSessionStorePlugin.class)).containsOnly(plugin1, plugin2);

        pluginManager.remove(WebSessionStorePlugin.class, plugin1.pluginName());
        assertThat(pluginManager.getGroupPlugins(WebSessionStorePlugin.class)).containsOnly(plugin2);
    }

    @Test
    void getPlugin() {
        pluginManager.register(WebSessionStorePlugin.class, new Test1WebSessionStorePlugin());
        assertThat(pluginManager.getPlugin(WebSessionStorePlugin.class, Test1WebSessionStorePlugin.class.getCanonicalName())).isNotNull();
    }

    @Test
    void getGroupPlugins() {
        var plugin = new Test1WebSessionStorePlugin();
        pluginManager.register(WebSessionStorePlugin.class, plugin);
        assertThat(pluginManager.getGroupPlugins(WebSessionStorePlugin.class)).containsOnly(plugin);
    }

    @Test
    void cleanup() {
        pluginManager.register(WebSessionStorePlugin.class, new Test1WebSessionStorePlugin());
        pluginManager.register(WebSessionStorePlugin.class, new Test2WebSessionStorePlugin());
        pluginManager.cleanup();
        assertThatThrownBy(() -> pluginManager.getGroupPlugins(WebSessionStorePlugin.class)).isInstanceOf(NullPointerException.class);
    }
}