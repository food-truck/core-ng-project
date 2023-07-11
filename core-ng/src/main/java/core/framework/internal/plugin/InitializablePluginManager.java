package core.framework.internal.plugin;

import core.framework.internal.module.ModuleContext;
import core.framework.plugin.InitializerPluginInitializable;
import core.framework.plugin.Plugin;
import core.framework.plugin.PluginInitializable;
import core.framework.plugin.PluginInitializer;
import core.framework.util.Lists;
import core.framework.util.Maps;
import core.framework.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;


/**
 * @author rickeyhong
 */
public final class InitializablePluginManager implements PluginManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InitializablePluginManager.class);
    private Map<Class<?>, List<Plugin>> pluginMap = Maps.newConcurrentHashMap();
    private List<PluginInitializer> pluginInitializers = Lists.newArrayList();
    private final ModuleContext context;

    public InitializablePluginManager(ModuleContext context) {
        this.context = context;
    }

    @Override
    public <T extends Plugin> void register(Class<T> plugin, T pluginImpl) {
        var plugins = pluginMap.computeIfAbsent(plugin, key -> new ArrayList<>());
        plugins.stream().filter(p -> p.pluginName().equals(pluginImpl.pluginName())).findAny().ifPresentOrElse(
            existedPlugin -> {
                throw new Error(Strings.format("deplicate plugin, pluginName: {}, existedPlugin: {}", pluginImpl.pluginName(), existedPlugin.getClass().getName()));
            }, () -> {
                if (pluginImpl instanceof InitializerPluginInitializable initializerPluginInitializable) {
                    if (pluginInitializers.isEmpty()) {
                        addHook();
                    }
                    pluginInitializers.add(initializerPluginInitializable.getPluginInitializer());
                } else if (pluginImpl instanceof PluginInitializable pluginInitializable) {
                    context.beanFactory.inject(pluginImpl);
                    pluginInitializable.initialize(context);
                }
                plugins.add(pluginImpl);
                LOGGER.info("Registered plugin: {}, order: {}", pluginImpl.pluginName(), pluginImpl.order());
            });
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
        pluginInitializers = null;
    }

    private void addHook() {
        context.prepareHook.invoke.add((invokeClass, method) -> signal(pluginInitializer -> pluginInitializer.signal(invokeClass, method)));
        context.prepareHook.bind.add((type, name, instance) -> signal(pluginInitializer -> pluginInitializer.signal(type, name, instance)));
        context.prepareHook.property.add((key, value) -> signal(pluginInitializer -> pluginInitializer.signal(key, value)));
    }

    private void signal(Predicate<PluginInitializer> handler) {
        if (pluginInitializers.isEmpty()) {
            return;
        }
        var iterator = pluginInitializers.iterator();
        while (iterator.hasNext()) {
            var pluginInitializer = iterator.next();
            if (handler.test(pluginInitializer)) {
                context.beanFactory.inject(pluginInitializer);
                pluginInitializer.initialize(context);
                iterator.remove();
            }
        }
    }
}