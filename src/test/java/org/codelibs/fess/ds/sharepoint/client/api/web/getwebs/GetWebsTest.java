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
package org.codelibs.fess.ds.sharepoint.client.api.web.getwebs;

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetWebsTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_parsesSubSitesAndFollowsNextLinkOnce() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String nextUrl = server.getBaseUrl() + "sites/test/_api/web/webinfos?nextpage=1";
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json",
                    "{\"value\":[" + "{\"Id\":\"11111111-1111-1111-1111-111111111111\",\"Title\":\"Child A\","
                            + "\"ServerRelativeUrl\":\"/sites/test/child-a\"}," + "{\"Id\":\"22222222-2222-2222-2222-222222222222\","
                            + "\"Title\":\"Child B\",\"ServerRelativeUrl\":\"/sites/test/child-b\"}" + "],\"odata.nextLink\":\"" + nextUrl
                            + "\"}");
            server.onPathQuery("/sites/test/_api/web/webinfos", "nextpage=1", "application/json", "{\"value\":[]}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebsResponse response = new GetWebs(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                final List<GetWebsResponse.SubSite> subSites = response.getSubSites();
                assertEquals("both children must be parsed", 2, subSites.size());
                assertEquals("first child id", "11111111-1111-1111-1111-111111111111", subSites.get(0).getId());
                assertEquals("first child title", "Child A", subSites.get(0).getTitle());
                assertEquals("first child path", "/sites/test/child-a", subSites.get(0).getServerRelativeUrl());
                assertEquals("second child id", "22222222-2222-2222-2222-222222222222", subSites.get(1).getId());
                assertEquals("second child title", "Child B", subSites.get(1).getTitle());
                assertEquals("second child path", "/sites/test/child-b", subSites.get(1).getServerRelativeUrl());
            }

            assertEquals("the next link must be followed exactly once", 2, server.getRecordedRequests().size());
        }
    }

    @Test
    public void test_requestsWebinfosWithoutPaging() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json", "{\"value\":[]}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                new GetWebs(httpClient, server.getBaseUrl() + "sites/test/", null).execute();
            }

            assertEquals(1, server.getRecordedRequests().size());
            final SharePointMockServer.RecordedRequest recorded = server.getRecordedRequests().get(0);
            assertEquals("/sites/test/_api/web/webinfos", recorded.getPath());
            assertNull(recorded.getQuery());
        }
    }

    @Test
    public void test_skipsAnEntryWithNoServerRelativeUrl() throws Exception {
        // SP.WebInformation has no absolute Url property - an entry with no ServerRelativeUrl at
        // all cannot be turned into a child site path, so it must not produce a SubSite that
        // would later be handed to SharePointClient#forSitePath with a null path.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json",
                    "{\"value\":[{\"Id\":\"11111111-1111-1111-1111-111111111111\",\"Title\":\"No Path\"}]}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebsResponse response = new GetWebs(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                assertEquals("an entry with no ServerRelativeUrl must be skipped", 0, response.getSubSites().size());
            }
        }
    }

    @Test
    public void test_skipsAnEntryWithABlankServerRelativeUrl() throws Exception {
        // An empty string is not null, so it would otherwise reach
        // SharePointClient.normalizeSitePath - which reinterprets a blank path as the root site's
        // own - turning a malformed entry into a silent, wrong match for the root instead of being
        // dropped here.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json",
                    "{\"value\":[{\"Id\":\"11111111-1111-1111-1111-111111111111\",\"Title\":\"Blank Path\","
                            + "\"ServerRelativeUrl\":\"\"}]}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebsResponse response = new GetWebs(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                assertEquals("an entry with a blank ServerRelativeUrl must be skipped", 0, response.getSubSites().size());
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
            final String selfUrl = server.getBaseUrl() + "sites/test/_api/web/webinfos?next=1";
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json",
                    "{\"value\":[{\"Id\":\"11111111-1111-1111-1111-111111111111\",\"Title\":\"Child\","
                            + "\"ServerRelativeUrl\":\"/sites/test/child\"}]," + "\"odata.nextLink\":\"" + selfUrl + "\"}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebsResponse response = new GetWebs(httpClient, server.getBaseUrl() + "sites/test/", null).execute();

                assertEquals("the loop must stop at its page bound, one child per page", 100, response.getSubSites().size());
            }
        }
    }
}
