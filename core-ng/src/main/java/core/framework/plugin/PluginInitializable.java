package core.framework.plugin;

import core.framework.internal.module.ModuleContext;

/**
 * @author rickeyhong
 */
public interface PluginInitializable {
    default boolean isAvailable() {
        return true;
    }

    void initialize(ModuleContext context);
}