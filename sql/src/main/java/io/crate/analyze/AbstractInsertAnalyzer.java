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

package io.crate.analyze;

import com.google.common.base.Preconditions;
import io.crate.exceptions.InvalidColumnNameException;
import io.crate.metadata.*;
import io.crate.sql.tree.Insert;

import java.util.ArrayList;
import java.util.Locale;

abstract class AbstractInsertAnalyzer {

    final Functions functions;
    final Schemas schemas;

    AbstractInsertAnalyzer(Functions functions, Schemas schemas) {
        this.functions = functions;
        this.schemas = schemas;
    }

    private IllegalArgumentException tooManyValuesException(int actual, int expected) {
        throw new IllegalArgumentException(
            String.format(Locale.ENGLISH,
                "INSERT statement contains a VALUES clause with too many elements (%s), expected (%s)",
                actual, expected));
    }

    void handleInsertColumns(Insert node, int maxInsertValues, AbstractInsertAnalyzedStatement context) {
        // allocate columnsLists
        int numColumns;

        if (node.columns().size() == 0) { // no columns given in statement
            numColumns = context.tableInfo().columns().size();
            if (maxInsertValues > numColumns) {
                throw tooManyValuesException(maxInsertValues, numColumns);
            }
            context.columns(new ArrayList<Reference>(numColumns));

            int i = 0;
            for (Reference columnInfo : context.tableInfo().columns()) {
                if (i >= maxInsertValues) {
                    break;
                }
                addColumn(columnInfo.ident().columnIdent(), context, i);
                i++;
            }

        } else {
            numColumns = node.columns().size();
            if (maxInsertValues > numColumns) {
                throw tooManyValuesException(maxInsertValues, numColumns);
            }
            context.columns(new ArrayList<Reference>(numColumns));
            for (int i = 0; i < node.columns().size(); i++) {
                addColumn(new ColumnIdent(node.columns().get(i)), context, i);
            }
        }

        if (context.primaryKeyColumnIndices().size() == 0 &&
            !(context.tableInfo().primaryKey().isEmpty() || context.tableInfo().hasAutoGeneratedPrimaryKey())) {
            boolean referenced = false;
            for (ColumnIdent columnIdent : context.tableInfo().primaryKey()) {
                if (checkReferencesForGeneratedColumn(columnIdent, context)) {
                    referenced = true;
                    break;
                }
            }
            if (!referenced) {
                throw new IllegalArgumentException("Primary key is required but is missing from the insert statement");
            }
        }
        ColumnIdent clusteredBy = context.tableInfo().clusteredBy();
        if (clusteredBy != null && !clusteredBy.name().equalsIgnoreCase("_id") && context.routingColumnIndex() < 0) {
            if (!checkReferencesForGeneratedColumn(clusteredBy, context)) {
                throw new IllegalArgumentException("Clustered by value is required but is missing from the insert statement");
            }
        }
    }

    private boolean checkReferencesForGeneratedColumn(ColumnIdent columnIdent, AbstractInsertAnalyzedStatement context) {
        Reference reference = context.tableInfo().getReference(columnIdent);
        if (reference instanceof GeneratedReference) {
            for (Reference referencedReference : ((GeneratedReference) reference).referencedReferences()) {
                for (Reference column : context.columns()) {
                    if (column.equals(referencedReference) ||
                        referencedReference.ident().columnIdent().isChildOf(column.ident().columnIdent())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * validates the column and sets primary key / partitioned by / routing information as well as a
     * column Reference to the context.
     * <p>
     * the created column reference is returned
     */
    private Reference addColumn(ColumnIdent column, AbstractInsertAnalyzedStatement context, int i) {
        Preconditions.checkArgument(!column.name().startsWith("_"), "Inserting system columns is not allowed");
        if (ColumnIdent.INVALID_COLUMN_NAME_PREDICATE.apply(column.name())) {
            throw new InvalidColumnNameException(column.name());
        }

        // set primary key column if found
        for (ColumnIdent pkIdent : context.tableInfo().primaryKey()) {
            if (pkIdent.getRoot().equals(column)) {
                context.addPrimaryKeyColumnIdx(i);
            }
        }

        // set partitioned column if found
        for (ColumnIdent partitionIdent : context.tableInfo().partitionedBy()) {
            if (partitionIdent.getRoot().equals(column)) {
                context.addPartitionedByIndex(i);
            }
        }

        // set routing if found
        ColumnIdent routing = context.tableInfo().clusteredBy();
        if (routing != null && (routing.equals(column) || routing.isChildOf(column))) {
            context.routingColumnIndex(i);
        }

        // ensure that every column is only listed once
        Reference columnReference = context.allocateUniqueReference(column);
        context.columns().add(columnReference);
        return columnReference;
    }
}