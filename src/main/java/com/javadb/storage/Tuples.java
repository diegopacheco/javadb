package com.javadb.storage;

import com.javadb.catalog.TableSchema;
import com.javadb.engine.Row;
import com.javadb.types.Value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class Tuples {

    private Tuples() {
    }

    public static byte[] serialize(TableSchema schema, Row row) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (int i = 0; i < schema.columns().size(); i++) {
                Value value = row.get(i);
                if (value.isNull()) {
                    out.writeByte(0);
                    continue;
                }
                out.writeByte(1);
                writeValue(out, value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Row deserialize(TableSchema schema, byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            List<Value> values = new ArrayList<>();
            for (int i = 0; i < schema.columns().size(); i++) {
                boolean present = in.readByte() == 1;
                if (!present) {
                    values.add(Value.NULL);
                    continue;
                }
                values.add(readValue(in, schema.columns().get(i).type()));
            }
            return new Row(values);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeValue(DataOutputStream out, Value value) throws IOException {
        switch (value) {
            case Value.IntVal v -> out.writeInt(v.v());
            case Value.LongVal v -> out.writeLong(v.v());
            case Value.DoubleVal v -> out.writeDouble(v.v());
            case Value.BoolVal v -> out.writeByte(v.v() ? 1 : 0);
            case Value.TextVal v -> {
                byte[] utf = v.v().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(utf.length);
                out.write(utf);
            }
            case Value.NullVal ignored -> throw new IllegalStateException("null has no encoding");
        }
    }

    private static Value readValue(DataInputStream in, com.javadb.types.DataType type) throws IOException {
        return switch (type) {
            case INT -> new Value.IntVal(in.readInt());
            case LONG -> new Value.LongVal(in.readLong());
            case DOUBLE -> new Value.DoubleVal(in.readDouble());
            case BOOL -> new Value.BoolVal(in.readByte() == 1);
            case TEXT -> {
                int length = in.readInt();
                byte[] utf = new byte[length];
                in.readFully(utf);
                yield new Value.TextVal(new String(utf, java.nio.charset.StandardCharsets.UTF_8));
            }
        };
    }
}
