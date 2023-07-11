package core.framework.plugin;

import core.framework.internal.module.ModuleContext;
import core.framework.util.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * @author rickeyhong
 */
public final class ConditionalPluginInitializer implements PluginInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalPluginInitializer.class);

    private final PluginInitializable pluginInitializable;

    private final Set<String> conditions;

    private final Set<String> oneVotePassConditions;

    private ConditionalPluginInitializer(PluginInitializable pluginInitializable, Set<String> conditions, Set<String> oneVotePassConditions) {
        this.pluginInitializable = pluginInitializable;
        this.conditions = conditions;
        this.oneVotePassConditions = oneVotePassConditions;

        conditions.forEach(condition -> LOGGER.info("Added condition: {}", condition));
        oneVotePassConditions.forEach(condition -> LOGGER.info("Added one vote pass condition: {}", condition));
    }

    @Override
    public boolean signal(Class<?> invokeClass, String method) {
        consumerCondition("invoke", Set.of(of(invokeClass).map(Class::getCanonicalName), of(method)));
        return isInitializable();
    }

    @Override
    public boolean signal(Type type, String name, Object instance) {
        consumerCondition("bind", Set.of(of(type).map(Type::getTypeName), ofNullable(name), ofNullable(instance).map(it -> it.getClass().getCanonicalName())));
        return isInitializable();
    }

    @Override
    public boolean signal(String key, String value) {
        consumerCondition("property", Set.of(of(key), ofNullable(value)));
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
        oneVotePassConditions.clear();
    }

    private void consumerCondition(String type, Set<Optional<String>> subConditions) {
        if (subConditions == null || subConditions.isEmpty()) {
            return;
        }
        var condition = new StringBuilder(type);
        subConditions.stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .forEach(subCondition -> {
                condition.append(':').append(subCondition);
                if (oneVotePassConditions.remove(condition.toString())) {
                    LOGGER.info("One vote pass condition: {}", condition);
                    conditions.clear();
                    return;
                }
                if (conditions.remove(condition.toString())) {
                    LOGGER.info("Pass condition: {}", condition);
                }
            });
    }

    public static class ConditionalPluginInitializerBuilder {
        public static ConditionalPluginInitializerBuilder builder(PluginInitializable pluginInitializable) {
            var builder = new ConditionalPluginInitializerBuilder();
            builder.pluginInitializable = pluginInitializable;
            return builder;
        }

        private PluginInitializable pluginInitializable;
        private final Set<String> conditions = Sets.newHashSet();
        private final Set<String> oneVotePassConditions = Sets.newHashSet();

        public ConditionalPluginInitializer build() {
            return new ConditionalPluginInitializer(pluginInitializable, conditions, oneVotePassConditions);
        }

        public Condition oneVotePassCondition() {
            return this.new Condition(true);
        }

        public Condition condition() {
            return this.new Condition(false);
        }

        public class Condition {
            private final boolean oneVotePass;

            public Condition(boolean oneVotePass) {
                this.oneVotePass = oneVotePass;
            }

            private void appendCondition(String type, Set<Optional<String>> subConditions) {
                if (subConditions == null || subConditions.isEmpty()) {
                    return;
                }
                var conditions = oneVotePass ? ConditionalPluginInitializerBuilder.this.oneVotePassConditions : ConditionalPluginInitializerBuilder.this.conditions;
                conditions.add(subConditions.stream()
                    .filter(Optional::isPresent)
                    .map(subCondition -> ':' + subCondition.get())
                    .reduce(new StringBuilder(type), StringBuilder::append, StringBuilder::append)
                    .toString());
            }

            public void invoke(Class<?> invokeClass, String method) {
                appendCondition("invoke", Set.of(of(invokeClass).map(Class::getCanonicalName), ofNullable(method)));
            }

            public void bind(Type type, String name, Class<?> instanceClass) {
                appendCondition("bind", Set.of(of(type).map(Type::getTypeName), ofNullable(name), ofNullable(instanceClass).map(Class::getCanonicalName)));
            }

            public void property(String key, String value) {
                appendCondition("property", Set.of(of(key), ofNullable(value)));
            }
        }
    }
}