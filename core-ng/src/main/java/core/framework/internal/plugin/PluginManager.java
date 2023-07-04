package core.framework.internal.plugin;

import core.framework.plugin.Plugin;

import java.util.List;

/**
 * @author rickeyhong
 */
public sealed interface PluginManager permits DefaultPluginManager {
    <T extends Plugin> void register(Class<T> group, T plugin);

    <T extends Plugin> T remove(Class<T> group, String pluginName);

    <T extends Plugin> T getPlugin(Class<T> group, String pluginName);

    <T extends Plugin> List<T> getGroupPlugins(Class<T> group);

    void cleanup();
}