/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.operation.reference.doc.lucene;

import io.crate.operation.collect.LuceneDocCollector;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.lookup.SearchLookup;

public class CollectorContext {

    private SearchContext searchContext;
    private SearchLookup searchLookup;
    private LuceneDocCollector.CollectorFieldsVisitor fieldsVisitor;
    private int jobSearchContextId;

    public CollectorContext() {
    }

    public SearchContext searchContext() {
        return searchContext;
    }

    public CollectorContext searchContext(SearchContext searchContext) {
        this.searchContext = searchContext;
        return this;
    }

    public CollectorContext visitor(LuceneDocCollector.CollectorFieldsVisitor visitor) {
        fieldsVisitor = visitor;
        return this;
    }

    public LuceneDocCollector.CollectorFieldsVisitor visitor(){
        return fieldsVisitor;
    }


    public CollectorContext jobSearchContextId(int jobSearchContextId) {
        this.jobSearchContextId = jobSearchContextId;
        return this;
    }

    public int jobSearchContextId() {
        return jobSearchContextId;
    }

    public CollectorContext searchLookup(SearchLookup searchLookup) {
        this.searchLookup = searchLookup;
        return this;
    }

    public SearchLookup searchLookup() {
        if (searchLookup == null) {
            return searchContext.lookup();
        }
        return searchLookup;
    }
}
