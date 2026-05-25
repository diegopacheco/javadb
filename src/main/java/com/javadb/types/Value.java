package com.javadb.types;

public sealed interface Value
        permits Value.IntVal, Value.LongVal, Value.DoubleVal,
                Value.TextVal, Value.BoolVal, Value.NullVal {

    record IntVal(int v) implements Value {}

    record LongVal(long v) implements Value {}

    record DoubleVal(double v) implements Value {}

    record TextVal(String v) implements Value {}

    record BoolVal(boolean v) implements Value {}

    record NullVal() implements Value {}

    Value NULL = new NullVal();

    default boolean isNull() {
        return this instanceof NullVal;
    }
}
