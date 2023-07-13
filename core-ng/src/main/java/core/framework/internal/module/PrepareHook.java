package core.framework.internal.module;

import core.framework.util.Lists;
import core.framework.util.TriConsumer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author rickeyhong
 */
public class PrepareHook {
    public List<BiConsumer<Class<?>, String>> invoke = Lists.newArrayList();

    public List<TriConsumer<Type, String, Object>> bind = Lists.newArrayList();

    public List<BiConsumer<String, String>> property = Lists.newArrayList();

    public void invoke(Class<?> invokeClass, String method) {
        if (invoke == null) {
            return;
        }
        invoke.forEach(it -> it.accept(invokeClass, method));
    }

    public void bind(Type type, String name, Object instance) {
        if (bind == null) {
            return;
        }
        bind.forEach(it -> it.accept(type, name, instance));
    }

    public void property(String key, String value) {
        if (property == null) {
            return;
        }
        property.forEach(it -> it.accept(key, value));
    }

    public void cleanup() {
        this.invoke = null;
        this.bind = null;
        this.property = null;
    }
}