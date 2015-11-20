package core.framework.api.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * @author neo
 */
public class ByteBufTest {
    @Test
    public void putByteBuffer() throws IOException {
        String text = "12345678901234567890";
        byte[] bytes = Strings.bytes(text);
        ByteBuf buffer = ByteBuf.newBuffer(128);
        buffer.put(ByteBuffer.wrap(bytes, 0, 10));
        buffer.put(ByteBuffer.wrap(bytes, 10, bytes.length - 10));

        assertEquals(text, buffer.text());
        assertEquals(bytes.length, buffer.position);
    }

    @Test
    public void putByteBufferWithExpectedLength() throws IOException {
        String text = "12345678901234567890";
        byte[] bytes = Strings.bytes(text);
        ByteBuf buffer = ByteBuf.newBufferWithExpectedLength(bytes.length);
        buffer.put(ByteBuffer.wrap(bytes));

        assertEquals(text, buffer.text());
        Assert.assertArrayEquals(bytes, buffer.bytes);
    }

    @Test
    public void putInputStream() throws IOException {
        byte[] bytes = Strings.bytes("12345678");
        ByteBuf buffer = ByteBuf.newBuffer(128);
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            buffer.put(stream);
        }
        Assert.assertArrayEquals(bytes, buffer.bytes());
    }

    @Test
    public void putInputStreamWithExpectedLength() throws IOException {
        byte[] bytes = Strings.bytes("12345678");
        ByteBuf buffer = ByteBuf.newBufferWithExpectedLength(bytes.length);
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            buffer.put(stream);
        }
        Assert.assertArrayEquals(bytes, buffer.bytes);
    }

    @Test
    public void growCapacity() throws IOException {
        String text = "12345678901234567890";
        byte[] bytes = Strings.bytes(text);
        ByteBuf buffer = ByteBuf.newBuffer(128);

        buffer.bytes = new byte[4];
        buffer.put(ByteBuffer.wrap(bytes, 0, 6));
        assertEquals("grow 2x size", 8, buffer.bytes.length);
        assertEquals(6, buffer.position);

        buffer.put(ByteBuffer.wrap(bytes, 6, 14));
        assertEquals("grow to available size", 20, buffer.bytes.length);
        assertEquals(20, buffer.position);

        assertEquals(text, buffer.text());
    }

    @Test
    public void putByte() {
        ByteBuf buffer = ByteBuf.newBuffer(2);
        for (int i = 0; i < 255; i++) {
            buffer.put((byte) i);
        }
        assertEquals(255, buffer.bytes().length);
        assertEquals((byte) 0, buffer.bytes()[0]);
        assertEquals((byte) 254, buffer.bytes()[254]);
    }

    @Test
    public void inputStream() throws IOException {
        byte[] bytes = Strings.bytes("1234567890\n1234567890");
        ByteBuf buffer = ByteBuf.newBuffer(128);
        buffer.put(ByteBuffer.wrap(bytes));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(buffer.inputStream()))) {
            String line1 = reader.readLine();
            assertEquals("1234567890", line1);
            String line2 = reader.readLine();
            assertEquals("1234567890", line2);
            String line3 = reader.readLine();
            Assert.assertNull(line3);
        }
    }

    @Test
    public void bytes() throws IOException {
        byte[] bytes = Strings.bytes("12345678901234567890");
        ByteBuf buffer = ByteBuf.newBuffer(128);
        buffer.put(ByteBuffer.wrap(bytes));
        Assert.assertArrayEquals(bytes, buffer.bytes());
    }

    @Test
    public void emptyByteBuff() throws IOException {
        ByteBuf buffer = ByteBuf.newBufferWithExpectedLength(0);
        assertEquals("", buffer.text());

        byte[] bytes = new byte[0];
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            buffer.put(stream);
        }

        Assert.assertArrayEquals(bytes, buffer.bytes);
    }
}