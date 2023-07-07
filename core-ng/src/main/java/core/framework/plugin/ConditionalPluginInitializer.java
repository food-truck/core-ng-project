package core.framework.plugin;

import core.framework.internal.module.ModuleContext;
import core.framework.util.Sets;
import core.framework.util.Strings;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * @author rickeyhong
 */
public class ConditionalPluginInitializer implements PluginInitializer {
    private final PluginInitializable pluginInitializable;

    private final Set<String> conditions;

    private ConditionalPluginInitializer(PluginInitializable pluginInitializable, Set<String> conditions) {
        this.pluginInitializable = pluginInitializable;
        this.conditions = conditions;
    }

    @Override
    public boolean signal(Class<?> initClass, String method) {
        var condition = "class:" + initClass.getCanonicalName();
        if (!Strings.isBlank(method)) {
            conditions.remove(condition + "." + method);
        }
        conditions.remove(condition);
        return isInitializable();
    }

    @Override
    public boolean signal(Type type, String name, Object instance) {
        var condition = "bindBean:" + type.getTypeName();
        conditions.remove(condition);
        if (instance != null) {
            condition += instance.getClass().getCanonicalName();
            conditions.remove(condition);
        }
        if (!Strings.isBlank(name)) {
            conditions.remove(condition + ":" + name);
        }
        return isInitializable();
    }

    @Override
    public boolean signal(String property, String value) {
        var condition = "property:" + property;
        if (!Strings.isBlank(value)) {
            conditions.remove(condition + "=" + value);
        }
        conditions.remove(condition);
        return isInitializable();
    }

    @Override
    public boolean isInitializable() {
        return conditions.isEmpty();
    }

    @Override
    public void initialize(ModuleContext context) {
        if (!isInitializable()) {
            throw new Error("Not Initializable!");
        }
        pluginInitializable.initialize(context);
    }

    static class ConditionalPluginInitializerBuilder {
        private PluginInitializable pluginInitializable;
        private final Set<String> conditions = Sets.newHashSet();

        public static ConditionalPluginInitializerBuilder builder(PluginInitializable pluginInitializable) {
            var builder = new ConditionalPluginInitializerBuilder();
            builder.pluginInitializable = pluginInitializable;
            return builder;
        }

        public ConditionalPluginInitializer build() {
            return new ConditionalPluginInitializer(pluginInitializable, conditions);
        }

        public ConditionalPluginInitializerBuilder condition(Class<?> initClass, String method) {
            var condition = "class:" + initClass.getCanonicalName();
            if (!Strings.isBlank(method)) {
                condition += "." + method;
            }
            conditions.add(condition);
            return this;
        }

        public ConditionalPluginInitializerBuilder condition(Type type, String name, Object instance) {
            var condition = "bindBean:" + type.getTypeName();
            if (instance != null) {
                condition += ":" + instance.getClass().getCanonicalName();
            }
            if (!Strings.isBlank(name)) {
                condition += ":" + name;  
            }
            conditions.add(condition);
            return this;
        }

        public ConditionalPluginInitializerBuilder condition(String property, String value) {
            var condition = "property:" + property;
            if (!Strings.isBlank(value)) {
                condition += "=" + value;
            }
            conditions.add(condition);
            return this;
        }
    }
}