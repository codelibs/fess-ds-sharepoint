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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

public class GetListsTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_parsesListsAndAppliesNoCrawlFlags() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath("/sites/test/_api/lists", "application/json", "fixtures/modern/lists.json");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final String siteUrl = server.getBaseUrl() + "sites/test/";
                final GetListsResponse response = new GetLists(httpClient, siteUrl, null).execute();

                final List<GetListsResponse.SharePointList> lists = response.getLists();
                assertEquals(3, lists.size());

                final GetListsResponse.SharePointList docLib = lists.get(0);
                assertEquals("Shared Documents", docLib.getListName());
                assertEquals("11111111-1111-1111-1111-111111111111", docLib.getId());
                assertEquals("Shared_x0020_Documents", docLib.getEntityTypeName());
                assertFalse("Shared Documents should not be flagged NoCrawl", docLib.isNoCrawl());
                assertFalse("Shared Documents should not be hidden", docLib.isHidden());

                final GetListsResponse.SharePointList userInfo = lists.get(2);
                assertEquals("User Information List", userInfo.getListName());
                assertTrue("User Information List is flagged NoCrawl in the fixture", userInfo.isNoCrawl());
                assertTrue("User Information List is hidden in the fixture", userInfo.isHidden());
            }
        }
    }

    @Test
    public void test_requestsApiListsWithoutPaging() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath("/sites/test/_api/lists", "application/json", "fixtures/modern/lists.json");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                new GetLists(httpClient, server.getBaseUrl() + "sites/test/", null).execute();
            }

            assertEquals(1, server.getRecordedRequests().size());
            final SharePointMockServer.RecordedRequest recorded = server.getRecordedRequests().get(0);
            assertEquals("/sites/test/_api/lists", recorded.getPath());
            // Current behavior: GetLists issues no paging parameters at all, so a site
            // with more lists than the server's default page size is silently truncated.
            // Phase 1 (fix/silent-data-loss) adds paging; this assertion will change then.
            assertNull(recorded.getQuery());
        }
    }
}
