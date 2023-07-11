package core.framework.internal.plugin;

import core.framework.plugin.Plugin;

import java.util.List;

/**
 * @author rickeyhong
 */
public sealed interface PluginManager permits InitializablePluginManager {
    <T extends Plugin> void register(Class<T> plugin, T pluginImpl);

    <T extends Plugin> void remove(Class<T> plugin);

    <T extends Plugin> List<T> getPlugins(Class<T> plugin);

    <T extends Plugin> void removeByPluginName(Class<T> plugin, String pluginName);

    <T extends Plugin> T getPluginByPluginName(Class<T> plugin, String pluginName);

    void cleanup();
}