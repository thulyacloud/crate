/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.operation.scalar;

import com.google.common.collect.ImmutableMap;
import io.crate.action.sql.SessionContext;
import io.crate.analyze.relations.AnalyzedRelation;
import io.crate.analyze.relations.TableRelation;
import io.crate.analyze.symbol.Function;
import io.crate.analyze.symbol.Literal;
import io.crate.analyze.symbol.Symbol;
import io.crate.metadata.*;
import io.crate.metadata.doc.DocSchemaInfo;
import io.crate.metadata.table.TableInfo;
import io.crate.metadata.table.TestingTableInfo;
import io.crate.operation.Input;
import io.crate.sql.tree.QualifiedName;
import io.crate.test.integration.CrateUnitTest;
import io.crate.testing.SqlExpressions;
import io.crate.types.ArrayType;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.apache.lucene.util.BytesRef;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

public abstract class AbstractScalarFunctionsTest extends CrateUnitTest {
    protected SqlExpressions sqlExpressions;
    protected Functions functions;
    protected Map<QualifiedName, AnalyzedRelation> tableSources;

    @Before
    public void prepareFunctions() throws Exception {
        TableInfo tableInfo = TestingTableInfo.builder(new TableIdent(DocSchemaInfo.NAME, "users"), null)
            .add("id", DataTypes.INTEGER)
            .add("name", DataTypes.STRING)
            .add("tags", new ArrayType(DataTypes.STRING))
            .add("age", DataTypes.INTEGER)
            .add("x", DataTypes.LONG)
            .add("shape", DataTypes.GEO_SHAPE)
            .add("timestamp", DataTypes.TIMESTAMP)
            .add("timezone", DataTypes.STRING)
            .add("interval", DataTypes.STRING)
            .add("time_format", DataTypes.STRING)
            .add("long_array", new ArrayType(DataTypes.LONG))
            .add("regex_pattern", DataTypes.STRING)
            .add("geoshape", DataTypes.GEO_SHAPE)
            .add("geopoint", DataTypes.GEO_POINT)
            .add("geostring", DataTypes.STRING)
            .add("is_awesome", DataTypes.BOOLEAN)
            .build();
        TableRelation tableRelation = new TableRelation(tableInfo);
        tableSources = ImmutableMap.<QualifiedName, AnalyzedRelation>of(new QualifiedName("users"), tableRelation);
        sqlExpressions = new SqlExpressions(tableSources);
        functions = sqlExpressions.getInstance(Functions.class);
    }

    /**
     * Assert that the functionExpression normalizes to the expectedSymbol
     * <p>
     * If the result of normalize is a Literal and all arguments were Literals evaluate is also called and
     * compared to the result of normalize - the resulting value of normalize must match evaluate.
     */
    @SuppressWarnings("unchecked")
    public void assertNormalize(String functionExpression, Matcher<? super Symbol> expectedSymbol) {
        assertNormalize(functionExpression, expectedSymbol, true);
    }

    public void assertNormalize(String functionExpression, Matcher<? super Symbol> expectedSymbol, boolean evaluate) {
        Symbol functionSymbol = sqlExpressions.asSymbol(functionExpression);
        if (functionSymbol instanceof Literal) {
            assertThat(functionSymbol, expectedSymbol);
            return;
        }
        Function function = (Function) functionSymbol;
        FunctionImplementation impl = functions.get(function.info().ident());
        assertThat(impl, Matchers.notNullValue());

        Symbol normalized = sqlExpressions.normalize(function);
        assertThat(
            String.format(Locale.ENGLISH, "expected <%s> to normalize to %s", functionExpression, expectedSymbol),
            normalized,
            expectedSymbol);

        if (evaluate && normalized instanceof Input && allArgsAreInputs(function.arguments())) {
            Input[] inputs = new Input[function.arguments().size()];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = ((Input) function.arguments().get(i));
            }
            Object expectedValue = ((Input) normalized).value();
            assertThat(((Scalar) impl).evaluate(inputs), is(expectedValue));
            assertThat(((Scalar) impl).compile(function.arguments()).evaluate(inputs), is(expectedValue));
        }
    }

    /**
     * asserts that the given functionExpression evaluates to the expectedValue.
     * If the functionExpression contains references the inputs will be used in the order the references appear.
     * <p>
     * E.g.
     * <code>
     * assertEvaluate("foo(name, age)", "expectedValue", inputForName, inputForAge)
     * </code>
     * or
     * <code>
     * assertEvaluate("foo('literalName', age)", "expectedValue", inputForAge)
     * </code>
     */
    @SuppressWarnings("unchecked")
    public void assertEvaluate(String functionExpression, Object expectedValue, Input... inputs) {
        Symbol functionSymbol = sqlExpressions.asSymbol(functionExpression);
        functionSymbol = sqlExpressions.normalize(functionSymbol);
        if (expectedValue instanceof String) {
            expectedValue = new BytesRef((String) expectedValue);
        }
        if (functionSymbol instanceof Literal) {
            assertThat(((Literal) functionSymbol).value(), is(expectedValue));
            return;
        }
        Function function = (Function) functionSymbol;
        Scalar scalar = (Scalar) functions.get(function.info().ident());
        assert scalar != null : "function must be registered";

        AssertingInput[] arguments = new AssertingInput[function.arguments().size()];
        int idx = 0;
        for (int i = 0; i < function.arguments().size(); i++) {
            Symbol arg = function.arguments().get(i);
            if (arg instanceof Input) {
                arguments[i] = new AssertingInput(((Input) arg));
            } else {
                arguments[i] = new AssertingInput(inputs[idx]);
                idx++;
            }
        }
        assertThat(scalar.compile(function.arguments()).evaluate((Input[] )arguments), is(expectedValue));
        for (AssertingInput argument : arguments) {
            argument.calls = 0;
        }
        assertThat(scalar.evaluate((Input[]) arguments), is(expectedValue));

    }

    public void assertCompile(String functionExpression,
                              com.google.common.base.Function<Scalar, Matcher<Scalar>> matcher,
                              Input... inputs) {
        Symbol functionSymbol = sqlExpressions.asSymbol(functionExpression);
        functionSymbol = sqlExpressions.normalize(functionSymbol);
        assertThat("function expression was normalized, compile would not be hit", functionSymbol, not(instanceOf(Literal.class)));
        Function function = (Function) functionSymbol;
        Scalar scalar = (Scalar) functions.get(function.info().ident());
        assert scalar != null : "function must be registered";

        AssertingInput[] arguments = new AssertingInput[function.arguments().size()];
        int idx = 0;
        for (int i = 0; i < function.arguments().size(); i++) {
            Symbol arg = function.arguments().get(i);
            if (arg instanceof Input) {
                arguments[i] = new AssertingInput(((Input) arg));
            } else {
                arguments[i] = new AssertingInput(inputs[idx]);
                idx++;
            }
        }
        Scalar compiled = scalar.compile(function.arguments());
        assertThat(compiled, matcher.apply(scalar));
    }


    private static boolean allArgsAreInputs(List<Symbol> arguments) {
        for (Symbol argument : arguments) {
            if (!(argument instanceof Input)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    protected <T extends FunctionImplementation> T getFunction(String functionName, DataType... argTypes) {
        return (T) getFunction(functionName, Arrays.asList(argTypes));
    }

    @SuppressWarnings("unchecked")
    protected <T extends FunctionImplementation> T getFunction(String functionName, List<DataType> argTypes) {
        return (T) functions.get(new FunctionIdent(functionName, argTypes));
    }

    protected Symbol normalize(String functionName, Object value, DataType type) {
        return normalize(functionName, Literal.of(type, value));
    }

    protected Symbol normalize(TransactionContext transactionContext, String functionName, Symbol... args) {
        DataType[] argTypes = new DataType[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].valueType();
        }
        FunctionImplementation<Function> function = getFunction(functionName, argTypes);
        return function.normalizeSymbol(new Function(function.info(),
            Arrays.asList(args)), transactionContext);
    }

    protected Symbol normalize(String functionName, Symbol... args) {
        return normalize(new TransactionContext(SessionContext.SYSTEM_SESSION), functionName, args);
    }

    private class AssertingInput implements Input {
        private final Input delegate;
        int calls = 0;

        AssertingInput(Input delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object value() {
            calls++;
            if (calls == 1) {
                return delegate.value();
            }
            throw new AssertionError("Input.value() should only be called once");
        }
    }
}