package core.framework.module;

import core.framework.internal.log.LogManager;
import core.framework.internal.module.ModuleContext;
import core.framework.plugin.Test1WebSessionStorePlugin;
import core.framework.plugin.WebSessionStorePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author rickeyhong 
 */
class PluginConfigTest {
    private PluginConfig pluginConfig;

    @BeforeEach
    void createPluginConfig() {
        this.pluginConfig = new PluginConfig();
        this.pluginConfig.initialize(new ModuleContext(new LogManager()), null);
    }

    @Test
    void plugin() {
        assertDoesNotThrow(() -> pluginConfig.plugin(WebSessionStorePlugin.class, new Test3WebSessionStorePlugin()));
        assertThatThrownBy(() -> pluginConfig.plugin(WebSessionStorePlugin.class, new Test1WebSessionStorePlugin())).isInstanceOf(Error.class);
    }

    static class Test3WebSessionStorePlugin extends Test1WebSessionStorePlugin { }
}