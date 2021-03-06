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

package io.crate.execution.jobs;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;

import org.elasticsearch.test.ESTestCase;
import org.junit.Before;
import org.junit.Test;

import io.crate.concurrent.limits.ConcurrencyLimit;

public class NodeLimitsTest extends ESTestCase {

    private NodeLimits nodeLimits = new NodeLimits();

    @Before
    public void setupJobsTracker() {
        nodeLimits = new NodeLimits();
    }

    @Test
    public void test_can_receive_limits_for_null_node() {
        ConcurrencyLimit limit = nodeLimits.get(null);
        assertThat(limit, notNullValue());
    }

    @Test
    public void test_successive_calls_return_same_instance_for_same_node() {
        assertThat(nodeLimits.get(null), sameInstance(nodeLimits.get(null)));
        assertThat(nodeLimits.get("n1"), sameInstance(nodeLimits.get("n1")));
        assertThat(nodeLimits.get("n1"), not(sameInstance(nodeLimits.get("n2"))));
    }
}
