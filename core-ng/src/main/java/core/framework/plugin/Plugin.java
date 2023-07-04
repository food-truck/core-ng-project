package core.framework.plugin;

/**
 * @author rickeyhong
 */
public sealed interface Plugin permits WebSessionStorePlugin {
    default String pluginName() {
        return getClass().getSimpleName();
    }
    
    default int order() {
        return 0;
    }
}