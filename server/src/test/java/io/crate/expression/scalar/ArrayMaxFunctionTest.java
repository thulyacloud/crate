package io.crate.expression.scalar;

import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.joda.time.Period;
import org.junit.Test;

import java.util.List;

import static io.crate.testing.Asserts.assertThrows;

public class ArrayMaxFunctionTest extends AbstractScalarFunctionsTest {

    @Test
    public void test_array_returns_min_element() {

        for(DataType t: DataTypes.PRIMITIVE_TYPES) {



            var expression = new StringBuilder("array_max(cast([")
                .append(listToCommaSeparatedString(List.of(1,2,3), t))
                .append("] as array(")
                .append(t.getName())
                .append(")))");

            Object expected;

            if (t.id() == DataTypes.INTERVAL.id()) {
                expected = new Period().withDays(3);
            }
            else {
                expected = t.implicitCast(3);
            }
            assertEvaluate(expression.toString(), expected);
        }
    }

    @Test
    public void test_null_array_results_in_null() {
        assertEvaluate("array_max(null::int[])", null);
    }

    @Test
    public void test_null_array_given_directly_results_in_null() {
       assertEvaluate("array_max(null)", null);
    }

    @Test
    public void test_empty_array_results_in_null() {
        assertEvaluate("array_max(cast([] as array(integer)))", null);
    }

    @Test
    public void test_empty_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_max([])", null),
            UnsupportedOperationException.class,
            "Unknown function: array_max([]), no overload found for matching argument types: (undefined_array).");
    }

}
