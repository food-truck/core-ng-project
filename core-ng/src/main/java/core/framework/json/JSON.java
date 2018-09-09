package core.framework.json;

import com.fasterxml.jackson.databind.JavaType;
import core.framework.impl.json.JSONMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

/**
 * @author neo
 */
public final class JSON {
    public static Object fromJSON(Type instanceType, String json) {
        try {
            JavaType javaType = JSONMapper.OBJECT_MAPPER.getTypeFactory().constructType(instanceType);
            return JSONMapper.OBJECT_MAPPER.readValue(json, javaType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T fromJSON(Class<T> instanceClass, String json) {
        try {
            return JSONMapper.OBJECT_MAPPER.readValue(json, instanceClass);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toJSON(Object instance) {
        try {
            return JSONMapper.OBJECT_MAPPER.writeValueAsString(instance);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T extends Enum<?>> T fromEnumValue(Class<T> valueClass, String jsonValue) {
        return JSONMapper.OBJECT_MAPPER.convertValue(jsonValue, valueClass);
    }

    public static <T extends Enum<?>> String toEnumValue(T value) {
        return JSONMapper.OBJECT_MAPPER.convertValue(value, String.class);
    }
}
