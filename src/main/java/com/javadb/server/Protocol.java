package com.javadb.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class Protocol {

    private Protocol() {
    }

    public static String readMessage(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
            buffer.write(b);
        }
        if (buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public static void writeMessage(OutputStream out, String message) throws IOException {
        out.write(message.getBytes(StandardCharsets.UTF_8));
        out.write(0);
        out.flush();
    }
}
