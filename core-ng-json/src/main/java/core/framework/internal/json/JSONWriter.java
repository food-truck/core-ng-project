package core.framework.internal.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * @author neo
 */
public final class JSONWriter<T> {
    private final ObjectWriter writer;

    public JSONWriter(Class<T> instanceClass) {
        this.writer = JSONMapper.OBJECT_MAPPER.writerFor(instanceClass);
    }

    // with jdk 11, write to String then covert to byte[] is faster than write to byte[]
    // toJSON won't throw exception especially instance class will be validated before
    public byte[] toJSON(T instance) {
        try {
            return writer.writeValueAsString(instance).getBytes(UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toJSONString(T instance) {
        try {
            return writer.writeValueAsString(instance);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
