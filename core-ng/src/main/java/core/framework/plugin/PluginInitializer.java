package core.framework.plugin;

import core.framework.internal.module.ModuleContext;

import java.lang.reflect.Type;

/**
 * @author rickeyhong
 */
public interface PluginInitializer {
    boolean signal(Class<?> invokeClass, String method);

    boolean signal(Type type, String name, Object instance);

    boolean signal(String key, String value);

    boolean isInitializable();

    void initialize(ModuleContext context);
}