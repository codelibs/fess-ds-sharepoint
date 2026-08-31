/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.ds.sharepoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.callback.IndexUpdateCallback;
import org.codelibs.fess.entity.DataStoreParams;

/**
 * An {@link IndexUpdateCallback} that records every document handed to it instead of indexing it,
 * so a test can inspect exactly what a data store would have sent to the index.
 */
class CapturingIndexUpdateCallback implements IndexUpdateCallback {

    /** The documents passed to {@link #store}, in the order they were received. */
    final List<Map<String, Object>> documents = new ArrayList<>();

    @Override
    public void store(final DataStoreParams paramMap, final Map<String, Object> dataMap) {
        documents.add(new LinkedHashMap<>(dataMap));
    }

    @Override
    public void commit() {
        // nothing to flush
    }

    @Override
    public long getDocumentSize() {
        return documents.size();
    }

    @Override
    public long getExecuteTime() {
        return 0L;
    }
}
