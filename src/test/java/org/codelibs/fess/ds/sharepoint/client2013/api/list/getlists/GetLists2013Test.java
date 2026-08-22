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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

public class GetLists2013Test extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * Pins the CURRENT, BROKEN behavior of GetLists2013.
     *
     * <p>GetLists2013 reads EntityTypeName and Hidden from the outer {@code dataMap}
     * instead of the per-entry {@code value} map, so entityTypeName is always null and
     * every list is skipped. Its NoCrawl default is also TRUE where the modern
     * GetLists uses FALSE. The net effect is that a SharePoint 2013 site crawl
     * enqueues zero lists.
     *
     * <p>THIS TEST ASSERTS A BUG. Phase 1 (fix/sp2013-list-crawl) fixes the
     * implementation, at which point this test must be inverted to expect 2 lists.
     */
    @Test
    public void test_currentBehavior_returnsNoLists() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath("/sites/test/_api/lists", "application/xml", "fixtures/sp2013/lists.xml");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetLists2013 api = new GetLists2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final GetListsResponse response = api.execute();

                assertEquals("SP2013 list crawl currently yields no lists (bug, fixed in Phase 1)", 0, response.getLists().size());
            }
        }
    }
}
