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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class SharePointDataStoreRoleTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void tearDown(final TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    /**
     * Pins the CURRENT, BROKEN role-merge behavior of SharePointDataStore.
     *
     * <p>AbstractDataStore always puts the data config's permissions into the
     * default data map, so {@code dataMap.containsKey(role)} is always true. When the
     * crawl result carries no role of its own, the else branch writes
     * {@code resultMap.get(role)} — which is null — over the configured permissions.
     * Documents indexed this way match no user's role query and become invisible.
     *
     * <p>THIS TEST ASSERTS A BUG. The fix makes the
     * configured permissions survive, at which point this test must be inverted.
     */
    @Test
    public void test_currentBehavior_emptyRoleWipesConfiguredPermissions() throws Exception {
        final String roleField = ComponentUtil.getFessConfig().getIndexFieldRole();

        final Map<String, Object> dataMap = new HashMap<>();
        final List<String> configuredPermissions = new ArrayList<>();
        configuredPermissions.add("1guest");
        dataMap.put(roleField, configuredPermissions);

        final Map<String, Object> resultMap = new HashMap<>();
        // The crawl produced no SharePoint-derived roles, so no role key is present.

        mergeRoleLikeDataStore(dataMap, resultMap, roleField);

        assertNull("configured permissions are currently wiped out when the crawl yields no role", dataMap.get(roleField));
    }

    /**
     * Mirrors the merge logic in SharePointDataStore#storeData so the behavior can be
     * pinned without standing up a full crawl. Keep in sync with the production code;
     * Fix both together.
     */
    private void mergeRoleLikeDataStore(final Map<String, Object> dataMap, final Map<String, Object> resultMap, final String roleField) {
        if (dataMap.containsKey(roleField) && resultMap.containsKey(roleField)) {
            final List<Object> roles = new ArrayList<>();
            if (dataMap.get(roleField) instanceof List<?> roleList) {
                roles.addAll(roleList);
            }
            if (resultMap.get(roleField) instanceof List<?> roleList) {
                roles.addAll(roleList);
            }
            dataMap.put(roleField, roles);
        } else {
            dataMap.put(roleField, resultMap.get(roleField));
        }
    }
}
