package core.framework.internal.plugin;

import core.framework.plugin.Plugin;
import core.framework.util.Maps;
import core.framework.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Comparator;
import java.util.Collections;
import java.util.Objects;


/**
 * @author rickeyhong
 */
public final class DefaultPluginManager implements PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPluginManager.class);
    private Map<Class<?>, List<Plugin>> pluginMap = Maps.newConcurrentHashMap();

    @Override
    public <T extends Plugin> void register(Class<T> plugin, T pluginImpl) {
        var plugins = pluginMap.computeIfAbsent(plugin, key -> new ArrayList<>());
        plugins.stream().filter(p -> p.pluginName().equals(pluginImpl.pluginName())).findAny().ifPresentOrElse(
            existedPlugin -> {
                throw new Error(Strings.format("deplicate plugin, pluginName: {}, existedPlugin: {}", pluginImpl.pluginName(), existedPlugin.getClass().getName()));
            }, () -> plugins.add(pluginImpl));
        LOGGER.info("Successful register plugin: {}, order: {}", pluginImpl.pluginName(), pluginImpl.order());
    }

    @Override
    public <T extends Plugin> List<T> getPlugins(Class<T> plugin) {
        return Optional.ofNullable(pluginMap.get(plugin))
            .map(plugins -> plugins.stream()
                .map(plugin::cast)
                .sorted(Comparator.comparingInt(Plugin::order))
                .toList())
            .orElse(Collections.emptyList());
    }

    @Override
    public <T extends Plugin> void remove(Class<T> plugin) {
        pluginMap.remove(plugin);
    }

    @Override
    public <T extends Plugin> void removeByPluginName(Class<T> plugin, String pluginName) {
        Optional.ofNullable(pluginMap.get(plugin)).ifPresent(plugins -> plugins.removeIf(p -> Objects.equals(p.pluginName(), pluginName)));
    }

    @Override
    public <T extends Plugin> T getPluginByPluginName(Class<T> plugin, String pluginName) {
        return Optional.ofNullable(pluginMap.get(plugin))
            .flatMap(plugins -> plugins.stream()
                .filter(p -> Objects.equals(p.pluginName(), pluginName))
                .findAny()
                .map(plugin::cast))
            .orElse(null);
    }

    @Override
    public void cleanup() {
        pluginMap = null;
    }
}