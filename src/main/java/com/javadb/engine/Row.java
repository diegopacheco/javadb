package com.javadb.engine;

import com.javadb.types.Value;

import java.util.List;

public record Row(List<Value> values) {

    public Value get(int i) {
        return values.get(i);
    }

    public int size() {
        return values.size();
    }
}
