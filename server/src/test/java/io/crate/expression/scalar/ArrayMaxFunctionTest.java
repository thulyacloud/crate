package io.crate.expression.scalar;

import org.junit.Test;

import static io.crate.testing.Asserts.assertThrows;

public class ArrayMaxFunctionTest extends AbstractScalarFunctionsTest {

    @Test
    public void test_array_returns_min_element() {
        assertEvaluate("array_max([3,2,1])", 3);
    }

    @Test
    public void test_null_array_results_in_null() {
        assertEvaluate("array_max(null::int[])", null);
    }

    @Test
    public void test_null_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_max(null)", null),
            IllegalArgumentException.class,
            "The inner type of the array argument `array_max` function cannot be undefined");
    }

    @Test
    public void test_empty_array_results_in_null() {
        assertEvaluate("array_max(cast([] as array(integer)))", null);
    }

    @Test
    public void test_empty_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_max([])", null),
            IllegalArgumentException.class,
            "The inner type of the array argument `array_max` function cannot be undefined");
    }

}
