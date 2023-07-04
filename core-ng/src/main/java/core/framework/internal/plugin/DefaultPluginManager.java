package core.framework.internal.plugin;

import core.framework.plugin.Plugin;
import core.framework.util.Maps;
import core.framework.util.Strings;

import java.util.*;

/**
 * @author rickeyhong
 */
@SuppressWarnings("unchecked")
public final class DefaultPluginManager implements PluginManager {
    private Map<Class<?>, Map<String, Plugin>> pluginMap = Maps.newConcurrentHashMap();

    @Override
    public <T extends Plugin> void register(Class<T> group, T plugin) {
        var plugins = pluginMap.computeIfAbsent(group, key -> Maps.newConcurrentHashMap());
        Optional.ofNullable(plugins.put(plugin.pluginName(), plugin)).ifPresent(existedPlugin -> {
            throw new Error(Strings.format("deplicate plugin, pluginName: {}, existedPlugin: {}", plugin.pluginName(), existedPlugin.getClass().getName()));
        });
    }

    @Override
    public <T extends Plugin> T remove(Class<T> group, String pluginName) {
        return Optional.ofNullable(pluginMap.get(group)).map(plugins -> (T) plugins.remove(pluginName)).orElse(null);
    }

    @Override
    public <T extends Plugin> T getPlugin(Class<T> group, String pluginName) {
        return Optional.ofNullable(pluginMap.get(group)).map(plugins -> (T) plugins.get(pluginName)).orElse(null);
    }

    @Override
    public <T extends Plugin> List<T> getGroupPlugins(Class<T> group) {
        return Optional.ofNullable(pluginMap.get(group))
            .map(plugins -> plugins.values().stream()
                .map(plugin -> (T) plugin)
                .sorted(Comparator.comparingInt(Plugin::order))
                .toList())
            .orElse(Collections.emptyList());
    }

    @Override
    public void cleanup() {
        pluginMap = null;
    }
}