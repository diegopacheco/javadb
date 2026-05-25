package com.javadb.types;

import com.javadb.types.Value.BoolVal;
import com.javadb.types.Value.DoubleVal;
import com.javadb.types.Value.IntVal;
import com.javadb.types.Value.LongVal;
import com.javadb.types.Value.NullVal;
import com.javadb.types.Value.TextVal;

public final class Values {

    private Values() {
    }

    public static boolean isNumeric(Value v) {
        return v instanceof IntVal || v instanceof LongVal || v instanceof DoubleVal;
    }

    public static double asDouble(Value v) {
        return switch (v) {
            case IntVal i -> i.v();
            case LongVal l -> l.v();
            case DoubleVal d -> d.v();
            default -> throw new IllegalStateException("not numeric: " + render(v));
        };
    }

    public static String render(Value v) {
        return switch (v) {
            case IntVal i -> Integer.toString(i.v());
            case LongVal l -> Long.toString(l.v());
            case DoubleVal d -> Double.toString(d.v());
            case TextVal t -> t.v();
            case BoolVal b -> Boolean.toString(b.v());
            case NullVal ignored -> "NULL";
        };
    }

    public static int compare(Value a, Value b) {
        if (isNumeric(a) && isNumeric(b)) {
            return Double.compare(asDouble(a), asDouble(b));
        }
        if (a instanceof TextVal ta && b instanceof TextVal tb) {
            return ta.v().compareTo(tb.v());
        }
        if (a instanceof BoolVal ba && b instanceof BoolVal bb) {
            return Boolean.compare(ba.v(), bb.v());
        }
        return Integer.compare(rank(a), rank(b));
    }

    private static int rank(Value v) {
        return switch (v) {
            case NullVal ignored -> 0;
            case BoolVal ignored -> 1;
            case IntVal ignored -> 2;
            case LongVal ignored -> 2;
            case DoubleVal ignored -> 2;
            case TextVal ignored -> 3;
        };
    }

    public static boolean truthy(Value v) {
        return v instanceof BoolVal b && b.v();
    }

    public static Value negate(Value v) {
        return switch (v) {
            case IntVal i -> new IntVal(-i.v());
            case LongVal l -> new LongVal(-l.v());
            case DoubleVal d -> new DoubleVal(-d.v());
            default -> throw new IllegalStateException("cannot negate: " + render(v));
        };
    }

    public static Value arithmetic(char op, Value a, Value b) {
        if (a.isNull() || b.isNull()) {
            return Value.NULL;
        }
        if (op == '+' && (a instanceof TextVal || b instanceof TextVal)) {
            return new TextVal(render(a) + render(b));
        }
        boolean asDouble = a instanceof DoubleVal || b instanceof DoubleVal || op == '/';
        if (asDouble) {
            double r = apply(op, asDouble(a), asDouble(b));
            return new DoubleVal(r);
        }
        long r = (long) apply(op, asDouble(a), asDouble(b));
        if (a instanceof LongVal || b instanceof LongVal || r > Integer.MAX_VALUE || r < Integer.MIN_VALUE) {
            return new LongVal(r);
        }
        return new IntVal((int) r);
    }

    private static double apply(char op, double a, double b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> a / b;
            default -> throw new IllegalArgumentException("unknown operator: " + op);
        };
    }

    public static Value coerce(Value v, DataType type) {
        if (v.isNull()) {
            return v;
        }
        return switch (type) {
            case INT -> new IntVal((int) requireLong(v, type));
            case LONG -> new LongVal(requireLong(v, type));
            case DOUBLE -> new DoubleVal(requireDouble(v, type));
            case TEXT -> {
                if (v instanceof TextVal) {
                    yield v;
                }
                throw typeError(v, type);
            }
            case BOOL -> {
                if (v instanceof BoolVal) {
                    yield v;
                }
                throw typeError(v, type);
            }
        };
    }

    private static long requireLong(Value v, DataType type) {
        if (v instanceof IntVal i) {
            return i.v();
        }
        if (v instanceof LongVal l) {
            return l.v();
        }
        if (v instanceof DoubleVal d && d.v() == Math.rint(d.v())) {
            return (long) d.v();
        }
        throw typeError(v, type);
    }

    private static double requireDouble(Value v, DataType type) {
        if (isNumeric(v)) {
            return asDouble(v);
        }
        throw typeError(v, type);
    }

    private static IllegalArgumentException typeError(Value v, DataType type) {
        return new IllegalArgumentException("value " + render(v) + " is not compatible with " + type);
    }
}
