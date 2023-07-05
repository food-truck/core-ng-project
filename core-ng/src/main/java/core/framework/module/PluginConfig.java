package core.framework.module;

import core.framework.internal.module.Config;
import core.framework.internal.module.ModuleContext;
import core.framework.plugin.Plugin;
import core.framework.plugin.PluginInitializable;
import core.framework.util.Lists;
import core.framework.util.Properties;
import core.framework.util.Strings;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @author rickeyhong
 */
public class PluginConfig extends Config {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginConfig.class);
    private ModuleContext context;
    private Set<Class<? extends Plugin>> usedGroup = new HashSet<>();

    @Override
    protected void initialize(ModuleContext context, @Nullable String name) {
        this.context = context;
        var pluginProperties = new Properties();
        pluginProperties.load("plugin.properties");
        pluginProperties.keys().forEach(group -> plugins(group, pluginProperties.get(group).orElseThrow()));

        context.startupHook.initialize.add(0, () -> usedGroup.forEach(group -> context.pluginManager.getGroupPlugins(group).forEach(plugin -> {
            context.beanFactory.inject(plugin);
            if (plugin instanceof PluginInitializable pluginInitializable) {
                pluginInitializable.initialize(context);
            }
        })));
        context.startupHook.start.add(() -> {
            context.pluginManager.cleanup();
            usedGroup = null;
        });
    }

    public <T extends Plugin> void plugin(Class<T> group, T plugin) {
        context.pluginManager.register(group, plugin);
        this.usedGroup.add(group);
        LOGGER.info("load plugin, group: {}, plugin: {}", group, plugin.pluginName());
    }

    @SuppressWarnings("unchecked")
    private void plugins(String groupClassName, String plugins) {
        LOGGER.info("plugins group: {}, plugins: {}", groupClassName, plugins);
        var classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> group;
        var pluginList = Lists.newArrayList();
        try {
            group = classLoader.loadClass(groupClassName);
            for (var plugin : plugins.split(",")) {
                pluginList.add(classLoader.loadClass(plugin.trim()).getConstructor().newInstance());
            }
        } catch (ReflectiveOperationException e) {
            throw new Error(Strings.format("plugins failure! groupClassName: {}, plugins: {}", groupClassName, plugins), e);
        }
        if (!Plugin.class.isAssignableFrom(group)) {
            throw new Error("Plugin must be extends from " + Plugin.class.getCanonicalName());
        }
        for (var plugin : pluginList) {
            if (!group.isAssignableFrom(plugin.getClass())) {
                throw new Error(Strings.format("Plugin `{}` must be implemention from {}", plugin.getClass().getCanonicalName(), group.getCanonicalName()));
            }
            plugin((Class<Plugin>) group, (Plugin) plugin);
        }
    }
}