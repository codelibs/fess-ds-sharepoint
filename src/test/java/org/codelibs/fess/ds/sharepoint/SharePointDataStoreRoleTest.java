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

import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Drives {@link SharePointDataStore#storeData} itself and inspects the document handed to the
 * callback, so the role merge is verified where it actually happens.
 */
public class SharePointDataStoreRoleTest extends UnitDsTestCase {

    private static final String ROLE_FIELD = "role";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        final CrawlerStatsHelper crawlerStatsHelper = new CrawlerStatsHelper();
        crawlerStatsHelper.init();
        ComponentUtil.register(crawlerStatsHelper, "crawlerStatsHelper");
    }

    private Map<String, Object> crawlSingleDocument(final Map<String, Object> crawlResult, final Map<String, Object> defaultDataMap) {
        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return new StubSharePointCrawler(List.of(crawlResult));
            }
        };
        final CapturingIndexUpdateCallback callback = new CapturingIndexUpdateCallback();
        dataStore.storeData(null, callback, new DataStoreParams(), new HashMap<>(), defaultDataMap);
        assertEquals("exactly one document must reach the index", 1, callback.documents.size());
        return callback.documents.get(0);
    }

    @Test
    public void test_configuredPermissionsSurviveWhenSharePointYieldsNoRole() {
        final Map<String, Object> defaultDataMap = new HashMap<>();
        defaultDataMap.put(ROLE_FIELD, new ArrayList<>(List.of("1alice", "2sales")));

        final Map<String, Object> crawlResult = new HashMap<>();
        crawlResult.put("title", "no-role document");

        final Map<String, Object> document = crawlSingleDocument(crawlResult, defaultDataMap);

        assertNotNull("the permissions configured on the data config must not be dropped", document.get(ROLE_FIELD));
        assertEquals("the configured permissions must be indexed verbatim", List.of("1alice", "2sales"), document.get(ROLE_FIELD));
    }

    @Test
    public void test_crawledRolesAreMergedOnTopOfConfiguredPermissions() {
        final Map<String, Object> defaultDataMap = new HashMap<>();
        defaultDataMap.put(ROLE_FIELD, new ArrayList<>(List.of("1alice")));

        final Map<String, Object> crawlResult = new HashMap<>();
        crawlResult.put(ROLE_FIELD, new ArrayList<>(List.of("2sales")));

        final Map<String, Object> document = crawlSingleDocument(crawlResult, defaultDataMap);

        assertEquals("both the configured and the crawled permissions must be indexed", List.of("1alice", "2sales"),
                document.get(ROLE_FIELD));
    }

    @Test
    public void test_roleIsRemovedFromTheScriptInputMap() {
        final Map<String, Object> defaultDataMap = new HashMap<>();
        defaultDataMap.put(ROLE_FIELD, new ArrayList<>(List.of("1alice")));

        final Map<String, Object> crawlResult = new HashMap<>();
        crawlResult.put(ROLE_FIELD, new ArrayList<>(List.of("2sales")));
        crawlSingleDocument(crawlResult, defaultDataMap);

        assertFalse("the role must be consumed so scripts cannot see it twice", crawlResult.containsKey(ROLE_FIELD));
    }
}
