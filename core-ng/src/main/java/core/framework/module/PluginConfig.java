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
    private Set<Class<? extends Plugin>> usedPlugins = new HashSet<>();

    @Override
    protected void initialize(ModuleContext context, @Nullable String name) {
        this.context = context;
        var pluginProperties = new Properties();
        pluginProperties.load("plugin.properties");
        pluginProperties.keys().forEach(plugin -> plugins(plugin, pluginProperties.get(plugin).orElseThrow()));

        context.startupHook.initialize.add(0, () -> usedPlugins.forEach(plugin -> context.pluginManager.getPlugins(plugin).forEach(pluginImpl -> {
            context.beanFactory.inject(pluginImpl);
            if (pluginImpl instanceof PluginInitializable pluginInitializable) {
                pluginInitializable.initialize(context);
            }
        })));
        context.startupHook.start.add(() -> {
            context.pluginManager.cleanup();
            usedPlugins = null;
        });
    }

    public <T extends Plugin> void plugin(Class<T> plugin, T pluginImpl) {
        context.pluginManager.register(plugin, pluginImpl);
        this.usedPlugins.add(plugin);
        LOGGER.info("load plugin, plugin: `{}`, pluginImpl: `{}`", plugin, pluginImpl.pluginName());
    }

    @SuppressWarnings("unchecked")
    private void plugins(String pluginClassName, String pluginImplClasses) {
        LOGGER.info("load plugins, plugin: `{}`, pluginImplClasses: `{}`", pluginClassName, pluginImplClasses);
        var classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> plugin;
        var pluginImpls = Lists.newArrayList();
        try {
            plugin = classLoader.loadClass(pluginClassName);
            for (var pluginImplClass : pluginImplClasses.split(",")) {
                pluginImpls.add(classLoader.loadClass(pluginImplClass.trim()).getConstructor().newInstance());
            }
        } catch (ReflectiveOperationException e) {
            throw new Error(Strings.format("Load plugins failure! pluginClassName: `{}`, pluginImplClasses: `{}`", pluginClassName, pluginImplClasses), e);
        }
        if (!Plugin.class.isAssignableFrom(plugin)) {
            throw new Error("Plugin must be extends from " + Plugin.class.getCanonicalName());
        }
        for (var pluginImpl : pluginImpls) {
            if (!plugin.isAssignableFrom(pluginImpl.getClass())) {
                throw new Error(Strings.format("Plugin `{}` must be implemention from `{}`", pluginImpl.getClass().getCanonicalName(), plugin.getCanonicalName()));
            }
            plugin((Class<Plugin>) plugin, (Plugin) pluginImpl);
        }
    }
}