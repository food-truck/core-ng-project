package core.framework.plugin;

import core.framework.internal.module.ModuleContext;
import core.framework.util.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * @author rickeyhong
 */
public final class ConditionalPluginInitializer implements PluginInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalPluginInitializer.class);

    public static final char SEPARATOR = ':';

    public static ConditionalPluginInitializerBuilder builder(BiConsumer<ModuleContext, List<String>> initializer) {
        return new ConditionalPluginInitializerBuilder(initializer);
    }

    private final BiConsumer<ModuleContext, List<String>> initializer;
    private final List<String> conditions;
    private final List<String> oneVotePassConditions;
    private final List<String> hitConditions = Lists.newArrayList();

    private ConditionalPluginInitializer(BiConsumer<ModuleContext, List<String>> initializer, List<String> conditions, List<String> oneVotePassConditions) {
        this.initializer = initializer;
        this.conditions = conditions;
        this.oneVotePassConditions = oneVotePassConditions;

        conditions.forEach(condition -> LOGGER.info("Added condition: {}", condition));
        oneVotePassConditions.forEach(condition -> LOGGER.info("Added one vote pass condition: {}", condition));
    }

    @Override
    public boolean signal(Class<?> invokeClass, String method) {
        consumerCondition("invoke", List.of(of(invokeClass).map(Class::getCanonicalName), of(method)));
        return isInitializable();
    }

    @Override
    public boolean signal(Type type, String name, Object instance) {
        consumerCondition("bind", List.of(of(type).map(Type::getTypeName), ofNullable(name), ofNullable(instance).map(it -> it.getClass().getCanonicalName())));
        return isInitializable();
    }

    @Override
    public boolean signal(String key, String value) {
        consumerCondition("property", List.of(of(key), ofNullable(value)));
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
        initializer.accept(context, hitConditions);
        oneVotePassConditions.clear();
    }

    private void consumerCondition(String type, List<Optional<String>> subConditions) {
        if (subConditions == null || subConditions.isEmpty()) {
            return;
        }
        var condition = new StringBuilder(type);
        subConditions.stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .forEach(subCondition -> {
                var conditionStr = condition.append(SEPARATOR).append(subCondition).toString();
                if (oneVotePassConditions.remove(conditionStr)) {
                    hitConditions.add(conditionStr);
                    LOGGER.info("One vote pass condition: {}", condition);
                    conditions.clear();
                    return;
                }
                if (conditions.remove(conditionStr)) {
                    hitConditions.add(conditionStr);
                    LOGGER.info("Pass condition: {}", condition);
                }
            });
    }

    public static final class ConditionalPluginInitializerBuilder {
        private final BiConsumer<ModuleContext, List<String>> initializer;
        private final List<String> conditions = Lists.newArrayList();
        private final List<String> oneVotePassConditions = Lists.newArrayList();

        private ConditionalPluginInitializerBuilder(BiConsumer<ModuleContext, List<String>> initializer) {
            this.initializer = initializer;
        }

        public ConditionalPluginInitializer build() {
            return new ConditionalPluginInitializer(initializer, conditions, oneVotePassConditions);
        }

        public Condition oneVotePassCondition() {
            return new Condition(this, true);
        }

        public Condition condition() {
            return new Condition(this, false);
        }
    }

    public record Condition(ConditionalPluginInitializerBuilder builder, boolean oneVotePass) {
        private ConditionalPluginInitializerBuilder appendCondition(String type, List<Optional<String>> subConditions) {
            if (subConditions != null && !subConditions.isEmpty()) {
                var conditions = oneVotePass ? builder.oneVotePassConditions : builder.conditions;
                conditions.add(subConditions.stream()
                    .filter(Optional::isPresent)
                    .map(subCondition -> SEPARATOR + subCondition.get())
                    .reduce(new StringBuilder(type), StringBuilder::append, StringBuilder::append)
                    .toString());
            }
            return builder;
        }

        public ConditionalPluginInitializerBuilder invoke(Class<?> invokeClass, String method) {
            return appendCondition("invoke", List.of(of(invokeClass).map(Class::getCanonicalName), ofNullable(method)));
        }

        public ConditionalPluginInitializerBuilder bind(Type type, String name, Class<?> instanceClass) {
            return appendCondition("bind", List.of(of(type).map(Type::getTypeName), ofNullable(name), ofNullable(instanceClass).map(Class::getCanonicalName)));
        }

        public ConditionalPluginInitializerBuilder property(String key, String value) {
            return appendCondition("property", List.of(of(key), ofNullable(value)));
        }
    }
}