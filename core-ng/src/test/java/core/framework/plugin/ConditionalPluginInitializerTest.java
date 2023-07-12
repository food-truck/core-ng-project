package core.framework.plugin;

import core.framework.internal.log.LogManager;
import core.framework.internal.module.ModuleContext;
import core.framework.module.App;
import core.framework.util.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rickeyhong
 */
class ConditionalPluginInitializerTest {
    private ModuleContext context;

    @BeforeEach
    void createConditionalPluginInitializer() {
        context = new ModuleContext(new LogManager());
    }

    @Test
    void signalInvoke() {
        var initializer = ConditionalPluginInitializer.builder(null)
            .condition().invoke(ConditionalPluginInitializerTest.class, "signalInvoke1")
            .condition().invoke(ConditionalPluginInitializerTest.class, "signalInvoke2")
            .build();
        assertThat(initializer.signal(ConditionalPluginInitializerTest.class, "signalInvoke_t_1")).isFalse();
        assertThat(initializer.signal(ConditionalPluginInitializerTest.class, "signalInvoke_t_2")).isFalse();

        assertThat(initializer.signal(ConditionalPluginInitializerTest.class, "signalInvoke1")).isFalse();
        assertThat(initializer.signal(ConditionalPluginInitializerTest.class, "signalInvoke2")).isTrue();
    }

    @Test
    void signalBind() {
        var initializer = ConditionalPluginInitializer.builder(null)
            .condition().bind(Types.generic(ConditionalPluginInitializerTest.class), null, null)
            .condition().bind(Types.generic(ConditionalPluginInitializer.class, String.class), null, null)
            .condition().bind(Types.generic(Test1WebSessionStorePlugin.class, String.class), "test", null)
            .condition().bind(Types.generic(Test2WebSessionStorePlugin.class, String.class), "test", String.class)
            .build();
        assertThat(initializer.signal(Types.generic(List.class), null, null)).isFalse();
        assertThat(initializer.signal(Types.generic(Set.class, String.class), null, null)).isFalse();

        assertThat(initializer.signal(Types.generic(ConditionalPluginInitializerTest.class), null, null)).isFalse();

        assertThat(initializer.signal(Types.generic(ConditionalPluginInitializer.class), null, null)).isFalse();
        assertThat(initializer.signal(Types.generic(ConditionalPluginInitializer.class, Integer.class), null, null)).isFalse();
        assertThat(initializer.signal(Types.generic(ConditionalPluginInitializer.class, String.class), null, null)).isFalse();

        assertThat(initializer.signal(Types.generic(Test1WebSessionStorePlugin.class, String.class), "test2", null)).isFalse();
        assertThat(initializer.signal(Types.generic(Test1WebSessionStorePlugin.class, String.class), "test", null)).isFalse();

        assertThat(initializer.signal(Types.generic(Test2WebSessionStorePlugin.class), null, null)).isFalse();
        assertThat(initializer.signal(Types.generic(Test2WebSessionStorePlugin.class, String.class), "test", 1)).isFalse();
        assertThat(initializer.signal(Types.generic(Test2WebSessionStorePlugin.class, String.class), "test", new AtomicBoolean())).isFalse();
        assertThat(initializer.signal(Types.generic(Test2WebSessionStorePlugin.class, String.class), "test", "")).isTrue();
    }

    @Test
    void signalProperty() {
        var initializer = ConditionalPluginInitializer.builder(null)
            .condition().property("key1", null)
            .condition().property("key2", null)
            .condition().property("key3", "value3")
            .build();
        assertThat(initializer.signal("key11", "value11")).isFalse();

        assertThat(initializer.signal("key1", null)).isFalse();
        assertThat(initializer.signal("key2", "value11")).isFalse();
        assertThat(initializer.signal("key3", "value3")).isTrue();
    }

    @Test
    void oneVotePassCondition() {
        var initializer = ConditionalPluginInitializer.builder((context, hitConditions) -> assertThat(hitConditions).containsOnlyOnce("property:oneVotePass:true"))
            .condition().invoke(App.class, "initializer")
            .condition().bind(Types.generic(List.class, String.class), null, null)
            .condition().property("key", "value")
            .oneVotePassCondition().property("oneVotePass", "true")
            .build();
        assertThat(initializer.signal("oneVotePass2", null)).isFalse();
        assertThat(initializer.signal("oneVotePass", "false")).isFalse();
        assertThat(initializer.signal("oneVotePass", "true")).isTrue();

        assertThat(initializer.signal("oneVotePass", "false")).isTrue();
        assertThat(initializer.signal("oneVotePass2", null)).isTrue();
        initializer.initialize(context);
    }
}