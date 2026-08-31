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
package org.codelibs.fess.ds.sharepoint.client2013.api.web.getwebs;

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.web.getwebs.GetWebsResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetWebs2013Test extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_parsesAllSubSitesFromTheAtomFeed() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/xml",
                    webInfosFeed(null, "11111111-1111-1111-1111-111111111111", "Child A", "/sites/test/child-a"));
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebs2013 api = new GetWebs2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetWebsResponse.SubSite> subSites = api.execute().getSubSites();

                assertEquals("every entry in the feed must be parsed", 1, subSites.size());
                final GetWebsResponse.SubSite child = subSites.get(0);
                assertEquals("child id", "11111111-1111-1111-1111-111111111111", child.getId());
                assertEquals("child title", "Child A", child.getTitle());
                assertEquals("child path", "/sites/test/child-a", child.getServerRelativeUrl());
            }
        }
    }

    @Test
    public void test_followsAtomNextLinkAcrossPages() throws Exception {
        // A feed with a <link rel="next"> used to be a dead end for GetLists2013's paging - the
        // same loop shape is used here, so it must be exercised the same way.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String nextUrl = server.getBaseUrl() + "sites/test/_api/web/webinfos?nextpage=1";
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/xml",
                    webInfosFeed(nextUrl, "11111111-1111-1111-1111-111111111111", "Child A", "/sites/test/child-a"));
            server.onPathQuery("/sites/test/_api/web/webinfos", "nextpage=1", "application/xml",
                    webInfosFeed(null, "22222222-2222-2222-2222-222222222222", "Child B", "/sites/test/child-b"));

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebs2013 api = new GetWebs2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetWebsResponse.SubSite> subSites = api.execute().getSubSites();

                assertEquals("both pages must be read", 2, subSites.size());
                assertEquals("Child A", subSites.get(0).getTitle());
                assertEquals("Child B", subSites.get(1).getTitle());
            }
        }
    }

    @Test
    public void test_skipsAnEntryWithABlankServerRelativeUrl() throws Exception {
        // An empty <d:ServerRelativeUrl/> is not absent, so it would otherwise reach
        // SharePointClient.normalizeSitePath - which reinterprets a blank path as the root site's
        // own - turning a malformed entry into a silent, wrong match for the root instead of being
        // dropped here.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/xml",
                    webInfosFeed(null, "11111111-1111-1111-1111-111111111111", "Blank Path", ""));
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebs2013 api = new GetWebs2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetWebsResponse.SubSite> subSites = api.execute().getSubSites();

                assertEquals("an entry with a blank ServerRelativeUrl must be skipped", 0, subSites.size());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_stopsFollowingAtomNextLinkAfterPageBound() throws Exception {
        // A server that always answers with a next link pointing back at itself must not be
        // followed forever. Without a bound this call never returns, which is what the timeout
        // above turns into a failure instead of a hung suite.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String selfUrl = server.getBaseUrl() + "sites/test/_api/web/webinfos?next=1";
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/xml",
                    webInfosFeed(selfUrl, "11111111-1111-1111-1111-111111111111", "Child A", "/sites/test/child-a"));

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetWebs2013 api = new GetWebs2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetWebsResponse.SubSite> subSites = api.execute().getSubSites();

                assertEquals("the loop must stop at its page bound, one child per page", 100, subSites.size());
            }
        }
    }

    private static String webInfosFeed(final String nextLink, final String id, final String title, final String serverRelativeUrl) {
        final StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" "
                + "xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">");
        if (nextLink != null) {
            xml.append("<link rel=\"next\" href=\"").append(nextLink).append("\"/>");
        }
        xml.append("<entry><content type=\"application/xml\"><m:properties>");
        xml.append("<d:Id m:type=\"Edm.Guid\">").append(id).append("</d:Id>");
        xml.append("<d:Title>").append(title).append("</d:Title>");
        xml.append("<d:ServerRelativeUrl>").append(serverRelativeUrl).append("</d:ServerRelativeUrl>");
        xml.append("</m:properties></content></entry>");
        xml.append("</feed>");
        return xml.toString();
    }
}
