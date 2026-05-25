package com.javadb.types;

public enum DataType {
    INT,
    LONG,
    DOUBLE,
    TEXT,
    BOOL;

    public static DataType from(String name) {
        return switch (name.toUpperCase()) {
            case "INT", "INTEGER" -> INT;
            case "LONG", "BIGINT" -> LONG;
            case "DOUBLE", "FLOAT", "REAL" -> DOUBLE;
            case "TEXT", "VARCHAR", "STRING" -> TEXT;
            case "BOOL", "BOOLEAN" -> BOOL;
            default -> throw new IllegalArgumentException("unknown type: " + name);
        };
    }
}
