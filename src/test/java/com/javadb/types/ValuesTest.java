package com.javadb.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesTest {

    @Test
    void comparesNumericAcrossTypes() {
        assertTrue(Values.compare(new Value.IntVal(2), new Value.LongVal(10L)) < 0);
        assertEquals(0, Values.compare(new Value.IntVal(5), new Value.DoubleVal(5.0)));
    }

    @Test
    void integerArithmeticStaysIntegral() {
        Value result = Values.arithmetic('+', new Value.IntVal(2), new Value.IntVal(3));
        assertEquals(new Value.IntVal(5), result);
    }

    @Test
    void divisionProducesDouble() {
        Value result = Values.arithmetic('/', new Value.IntVal(7), new Value.IntVal(2));
        assertEquals(new Value.DoubleVal(3.5), result);
    }

    @Test
    void coercionTargetsColumnType() {
        assertEquals(new Value.LongVal(4L), Values.coerce(new Value.IntVal(4), DataType.LONG));
        assertEquals(new Value.DoubleVal(4.0), Values.coerce(new Value.IntVal(4), DataType.DOUBLE));
    }

    @Test
    void arithmeticWithNullIsNull() {
        assertTrue(Values.arithmetic('+', Value.NULL, new Value.IntVal(1)).isNull());
    }
}
