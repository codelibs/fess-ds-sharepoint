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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

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
            // GetLists never adds its own paging parameters - it lets the server drive paging
            // through odata.nextLink instead. This fixture has none, so exactly one request is
            // made and it carries no query string of our own making.
            assertNull(recorded.getQuery());
        }
    }

    @Test
    public void test_followsODataNextLinkAcrossPages() throws Exception {
        // A response that includes odata.nextLink used to be a dead end: GetLists read only the
        // first page and never followed it, silently truncating a site with more lists than the
        // server's own default page size.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String nextUrl = server.getBaseUrl() + "sites/test/_api/lists?nextpage=1";
            server.onPathStatus("/sites/test/_api/lists", 200, "application/json",
                    "{\"value\":[{\"Id\":\"11111111-1111-1111-1111-111111111111\",\"Title\":\"List A\","
                            + "\"EntityTypeName\":\"GenericList\",\"NoCrawl\":false,\"Hidden\":false}]," + "\"odata.nextLink\":\"" + nextUrl
                            + "\"}");
            server.onPathQuery("/sites/test/_api/lists", "nextpage=1", "application/json",
                    "{\"value\":[{\"Id\":\"22222222-2222-2222-2222-222222222222\",\"Title\":\"List B\","
                            + "\"EntityTypeName\":\"GenericList\",\"NoCrawl\":false,\"Hidden\":false}]}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListsResponse response = new GetLists(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                final List<GetListsResponse.SharePointList> lists = response.getLists();
                assertEquals("both pages must be read", 2, lists.size());
                assertEquals("List A", lists.get(0).getListName());
                assertEquals("List B", lists.get(1).getListName());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_stopsFollowingNextLinkAfterPageBound() throws Exception {
        // A server that always answers with a nextLink pointing back at itself must not be
        // followed forever. Without a bound this call never returns, which is what the timeout
        // above turns into a failure instead of a hung suite.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String selfUrl = server.getBaseUrl() + "sites/test/_api/lists?next=1";
            // Registered by path only, so it answers the initial request and every "?next=1"
            // follow-up identically - exactly how a server that never advances would behave.
            server.onPathStatus("/sites/test/_api/lists", 200, "application/json",
                    "{\"value\":[{\"Id\":\"11111111-1111-1111-1111-111111111111\","
                            + "\"Title\":\"List A\",\"EntityTypeName\":\"GenericList\",\"NoCrawl\":false,\"Hidden\":false}],"
                            + "\"odata.nextLink\":\"" + selfUrl + "\"}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListsResponse response = new GetLists(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                assertEquals("the loop must stop at its page bound, one list per page", 100, response.getLists().size());
            }
        }
    }
}
