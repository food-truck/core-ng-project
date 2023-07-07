package core.framework.plugin;

import core.framework.internal.module.ModuleContext;

import java.lang.reflect.Type;

/**
 * @author rickeyhong 
 */
public interface PluginInitializer {
    boolean signal(Class<?> initClass, String method);

    boolean signal(Type type, String name, Object instance);

    boolean signal(String property, String value);
    
    boolean isInitializable();

    void initialize(ModuleContext context);
}