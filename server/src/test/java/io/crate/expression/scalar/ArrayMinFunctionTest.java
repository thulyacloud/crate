package io.crate.expression.scalar;

import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.crate.testing.Asserts.assertThrows;

public class ArrayMinFunctionTest extends AbstractScalarFunctionsTest {

    @Test
    public void test_array_returns_min_element() {
        for(DataType t: DataTypes.PRIMITIVE_TYPES) {

            var expression = new StringBuilder("array_min(cast([")
                .append(listToCommaSeparatedString(List.of(3,2,1), t))
                .append("] as array(")
                .append(t.getName())
                .append(")))");

            Object expected;

            if (t.id() == DataTypes.INTERVAL.id()) {
                expected = new Period().withDays(1);
            }
            else {
                expected = t.implicitCast(1);
            }
            assertEvaluate(expression.toString(), expected);
        }
    }

    @Test
    public void test_null_array_results_in_null() {
        assertEvaluate("array_min(null::int[])", null);
    }

    @Test
    public void test_null_array_given_directly_results_in_null() {
        assertEvaluate("array_min(null)", null);
    }

    @Test
    public void test_empty_array_results_in_null() {
        assertEvaluate("array_min(cast([] as array(integer)))", null);
    }

    @Test
    public void test_empty_array_given_directly_throws_exception() {
        assertThrows(() -> assertEvaluate("array_min([])", null),
            UnsupportedOperationException.class,
            "Unknown function: array_min([]), no overload found for matching argument types: (undefined_array).");
    }
}
