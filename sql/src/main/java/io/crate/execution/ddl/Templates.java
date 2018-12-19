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

package io.crate.execution.ddl;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import io.crate.metadata.PartitionName;
import io.crate.metadata.RelationName;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.common.compress.CompressedXContent;

import java.io.IOException;
import java.util.Collections;

public final class Templates {

    public static IndexTemplateMetaData.Builder copyWithNewName(IndexTemplateMetaData source, RelationName newName) {
        String targetTemplateName = PartitionName.templateName(newName.schema(), newName.name());
        String targetAlias = newName.indexNameOrAlias();
        IndexTemplateMetaData.Builder templateBuilder = IndexTemplateMetaData
            .builder(targetTemplateName)
            .patterns(Collections.singletonList(PartitionName.templatePrefix(newName.schema(), newName.name())))
            .settings(source.settings())
            .order(source.order())
            .putAlias(AliasMetaData.builder(targetAlias).build())
            .version(source.version());

        for (ObjectObjectCursor<String, CompressedXContent> mapping : source.mappings()) {
            try {
                templateBuilder.putMapping(mapping.key, mapping.value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return templateBuilder;
    }
}