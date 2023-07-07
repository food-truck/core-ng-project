package core.framework.plugin;

import core.framework.internal.module.ModuleContext;

/**
 * @author rickeyhong
 */
public interface PluginInitializable {
    void initialize(ModuleContext context);

    default boolean isAvailable() {
        return true;
    }
}