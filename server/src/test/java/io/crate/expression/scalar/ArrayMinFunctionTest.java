package io.crate.expression.scalar;

import org.junit.Test;

import static io.crate.testing.Asserts.assertThrows;

public class ArrayMinFunctionTest extends AbstractScalarFunctionsTest {

    @Test
    public void test_array_returns_min_element() {
        assertEvaluate("array_min([3,2,1])", 1);
    }

    @Test
    public void test_null_array_results_in_null() {
        assertEvaluate("array_min(null::int[])", null);
    }

    @Test
    public void test_null_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_min(null)", null),
        IllegalArgumentException.class,
            "The inner type of the array argument `array_min` function cannot be undefined");
    }

    @Test
    public void test_empty_array_results_in_null() {
        assertEvaluate("array_min(cast([] as array(integer)))", null);
    }

    @Test
    public void test_empty_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_min([])", null),
            IllegalArgumentException.class,
            "The inner type of the array argument `array_min` function cannot be undefined");
    }
}
