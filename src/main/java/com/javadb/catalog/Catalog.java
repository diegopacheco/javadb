package com.javadb.catalog;

import com.javadb.types.DataType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Catalog {

    private final Path file;
    private final Map<String, TableSchema> tables = new LinkedHashMap<>();
    private final List<IndexDef> indexes = new ArrayList<>();

    public Catalog(Path directory) {
        this.file = directory.resolve("catalog.txt");
        load();
    }

    public boolean hasTable(String name) {
        return tables.containsKey(name.toLowerCase());
    }

    public TableSchema table(String name) {
        TableSchema schema = tables.get(name.toLowerCase());
        if (schema == null) {
            throw new IllegalArgumentException("unknown table: " + name);
        }
        return schema;
    }

    public List<TableSchema> tables() {
        return new ArrayList<>(tables.values());
    }

    public List<IndexDef> indexes() {
        return new ArrayList<>(indexes);
    }

    public void addTable(TableSchema schema) {
        if (hasTable(schema.name())) {
            throw new IllegalArgumentException("table already exists: " + schema.name());
        }
        tables.put(schema.name().toLowerCase(), schema);
        save();
    }

    public void addIndex(IndexDef index) {
        indexes.add(index);
        save();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith("T:")) {
                    parseTable(line.substring(2));
                } else if (line.startsWith("I:")) {
                    parseIndex(line.substring(2));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void parseTable(String body) {
        int sep = body.indexOf(':');
        String name = body.substring(0, sep);
        String columnsPart = body.substring(sep + 1);
        List<Column> columns = new ArrayList<>();
        for (String pair : columnsPart.split(";")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split(",");
            columns.add(new Column(parts[0], DataType.valueOf(parts[1])));
        }
        tables.put(name.toLowerCase(), new TableSchema(name, columns));
    }

    private void parseIndex(String body) {
        String[] parts = body.split(":");
        indexes.add(new IndexDef(parts[0], parts[1], parts[2]));
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (TableSchema schema : tables.values()) {
            sb.append("T:").append(schema.name()).append(':');
            for (Column column : schema.columns()) {
                sb.append(column.name()).append(',').append(column.type().name()).append(';');
            }
            sb.append('\n');
        }
        for (IndexDef index : indexes) {
            sb.append("I:").append(index.name()).append(':')
                    .append(index.table()).append(':').append(index.column()).append('\n');
        }
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
