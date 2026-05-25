package com.javadb.catalog;

import java.util.List;

public record TableSchema(String name, List<Column> columns) {

    public int indexOf(String column) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    public Column column(String name) {
        int i = indexOf(name);
        if (i < 0) {
            throw new IllegalArgumentException("unknown column: " + name + " in table " + this.name);
        }
        return columns.get(i);
    }

    public List<String> columnNames() {
        return columns.stream().map(Column::name).toList();
    }
}
