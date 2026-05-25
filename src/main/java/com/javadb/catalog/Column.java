package com.javadb.catalog;

import com.javadb.types.DataType;

public record Column(String name, DataType type) {
}
