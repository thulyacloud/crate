/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.expression.scalar;

import io.crate.data.Input;
import io.crate.execution.engine.aggregation.impl.KahanSummationForDouble;
import io.crate.execution.engine.aggregation.impl.KahanSummationForFloat;
import io.crate.metadata.NodeContext;
import io.crate.metadata.Scalar;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.functions.Signature;
import io.crate.types.ArrayType;
import io.crate.types.DataType;
import io.crate.types.DataTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;

import static io.crate.expression.scalar.array.ArrayArgumentValidators.ensureInnerTypeIsNotUndefined;

public class ArraySumFunction<T extends Number, R extends Number> extends Scalar<R, List<T>> {

    public static final String NAME = "array_sum";

    // ZERO is used in each evaluate call, safe to use across the different rows as Number addition is immutable function.
    private final R ZERO;
    private final DataType<R> returnType;
    private BiFunction<R,T,R> summationOperator; //First term holds increment sum, type is same with the return type.

    public static void register(ScalarFunctionModule module) {
        module.register(
            Signature.scalar(
                NAME,
                new ArrayType(DataTypes.NUMERIC).getTypeSignature(),
                DataTypes.NUMERIC.getTypeSignature()
            ),
            ArraySumFunction::new
        );

        for (var supportedType : DataTypes.NUMERIC_PRIMITIVE_TYPES) {
            DataType inputDependantOutputType = DataTypes.LONG;
            if (supportedType == DataTypes.FLOAT || supportedType == DataTypes.DOUBLE) {
                inputDependantOutputType = supportedType;
            }

            module.register(
                Signature.scalar(
                    NAME,
                    new ArrayType(supportedType).getTypeSignature(),
                    inputDependantOutputType.getTypeSignature()
                ),
                ArraySumFunction::new
            );
        }
    }

    private final Signature signature;
    private final Signature boundSignature;

    private ArraySumFunction(Signature signature, Signature boundSignature) {
        this.signature = signature;
        this.boundSignature = boundSignature;

        returnType = (DataType<R>) signature.getReturnType().createType();

        ZERO = returnType.implicitCast(0);

        if (returnType.equals(DataTypes.NUMERIC)) {
            summationOperator = (x, y) -> (R) ((BigDecimal) x).add((BigDecimal) y);
        } else {
            summationOperator = (x, y) -> (R) Long.valueOf(Math.addExact(x.longValue(), y.longValue()));
        }
        /* else if (returnType.equals(DataTypes.FLOAT)) {
            var kahanSummationForFloat = new KahanSummationForFloat();
            // In case single ArraySumFunction called from multiple threads
            // cannot use single instance for all arrays in all rows as every
            // evaluate needs it's own KahanSummationForFloat instance (own accumulated error).
            summationOperator = (x, y) -> kahanSummationForFloat.sum((float) x, (float) y);
        }
        else if (returnType.equals(DataTypes.DOUBLE)) {
            var kahanSummationForDouble = new KahanSummationForDouble();
            summationOperator = (x, y) -> kahanSummationForDouble.sum((double) x, (double) y);
        } */

        ensureInnerTypeIsNotUndefined(boundSignature.getArgumentDataTypes(), signature.getName().name());
    }

    @Override
    public Signature signature() {
        return signature;
    }

    @Override
    public Signature boundSignature() {
        return boundSignature;
    }

    @Override
    public R evaluate(TransactionContext txnCtx, NodeContext nodeCtx, Input[] args) {
        List<T> values = (List) args[0].value();
        if (values == null || values.isEmpty()) {
            return ZERO;
        }

        // These 2 'if' blocks below can be removed and operator.apply() can be used for Double and Float
        // if ScalarFunction can have single Kahan-State for all rows, i.e rows in a single machine/JVM are processed in a single thread
        // and error can be reset before/after each evaluate call.
        if (returnType.equals(DataTypes.FLOAT)) {
            var kahanSummationForFloat = new KahanSummationForFloat();
            float floatSum = 0;
            for (int i = 0; i < values.size(); i++) {
                var item = values.get(i);
                if (item != null) {
                    floatSum = kahanSummationForFloat.sum(floatSum, (float) item);
                }
            }
            return (R) Float.valueOf(floatSum);
        }

        if (returnType.equals(DataTypes.DOUBLE)) {
            var kahanSummationForDouble = new KahanSummationForDouble();
            double doubleSum = 0;
            for (int i = 0; i < values.size(); i++) {
                var item = values.get(i);
                if (item != null) {
                    doubleSum = kahanSummationForDouble.sum(doubleSum, (double) item);
                }
            }
            return (R) Double.valueOf(doubleSum);
        }


        R sum = ZERO;
        for (int i = 0; i < values.size(); i++) {
            var item = values.get(i);
            if (item != null) {
                sum = summationOperator.apply(sum, item);
            }
        }
        return sum;
    }
}
