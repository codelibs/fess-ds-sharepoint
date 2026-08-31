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

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetLists2013Test extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * Confirms GetLists2013 parses every entry in the Atom feed.
     *
     * <p>GetLists2013 used to read EntityTypeName and Hidden from the outer
     * {@code dataMap} instead of the per-entry {@code value} map, so entityTypeName was
     * always null and every list was skipped before the rest of the mapping ever ran.
     * Its NoCrawl default was also TRUE where every other list parser in the tree
     * defaults to FALSE. The net effect was that a SharePoint 2013 site crawl enqueued
     * zero lists.
     *
     * <p><b>Tripwire entry:</b> {@code fixtures/sp2013/lists.xml} carries a third
     * {@code <entry>} (Id {@code 44444444-...}) with {@code <d:Hidden>true</d:Hidden>}
     * and no {@code <d:NoCrawl>} element at all. The count alone cannot tell a partial
     * fix from a complete one - fixing only the {@code EntityTypeName} line already
     * brings the count to 3, since GetLists2013 does not filter by NoCrawl or Hidden
     * itself. What only this entry's {@code isNoCrawl()}/{@code isHidden()} pair can
     * show is whether the other two defects are still there: a wrong NoCrawl default
     * of TRUE reports {@code isNoCrawl()==true} for an entry with no NoCrawl element,
     * and reading Hidden from the wrong map reports {@code isHidden()==false} despite
     * the fixture's {@code <d:Hidden>true</d:Hidden>}. Do not delete it as noise.
     */
    @Test
    public void test_parsesAllListsFromTheAtomFeed() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath("/sites/test/_api/lists", "application/xml", "fixtures/sp2013/lists.xml");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetLists2013 api = new GetLists2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetListsResponse.SharePointList> lists = api.execute().getLists();

                assertEquals("every entry in the feed must be parsed", 3, lists.size());

                final GetListsResponse.SharePointList docLib = lists.get(0);
                assertEquals("first list id", "11111111-1111-1111-1111-111111111111", docLib.getId());
                assertEquals("first list name", "Shared Documents", docLib.getListName());
                assertEquals("first list entity type", "Shared_x0020_Documents", docLib.getEntityTypeName());
                assertFalse("Shared Documents is not flagged NoCrawl in the fixture", docLib.isNoCrawl());
                assertFalse("Shared Documents is not hidden in the fixture", docLib.isHidden());

                // This entry is what separates a complete fix from a partial one. It carries
                // Hidden=true and no NoCrawl element at all, so it fails if either the per-entry
                // map or the NoCrawl default is still wrong.
                final GetListsResponse.SharePointList tripwire = lists.get(2);
                assertEquals("tripwire list id", "44444444-4444-4444-4444-444444444444", tripwire.getId());
                assertFalse("a list with no NoCrawl element must default to crawlable", tripwire.isNoCrawl());
                assertTrue("Hidden must be read from the entry, not from the feed", tripwire.isHidden());
            }
        }
    }

    @Test
    public void test_followsAtomNextLinkAcrossPages() throws Exception {
        // A feed with a <link rel="next"> used to be a dead end: GetLists2013 read only the
        // first page and never followed it, silently truncating a site with more lists than the
        // server's own default page size.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String nextUrl = server.getBaseUrl() + "sites/test/_api/lists?nextpage=1";
            server.onPathStatus("/sites/test/_api/lists", 200, "application/xml",
                    listsFeed(nextUrl, "11111111-1111-1111-1111-111111111111", "List A"));
            server.onPathQuery("/sites/test/_api/lists", "nextpage=1", "application/xml",
                    listsFeed(null, "22222222-2222-2222-2222-222222222222", "List B"));

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetLists2013 api = new GetLists2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetListsResponse.SharePointList> lists = api.execute().getLists();

                assertEquals("both pages must be read", 2, lists.size());
                assertEquals("List A", lists.get(0).getListName());
                assertEquals("List B", lists.get(1).getListName());
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
            final String selfUrl = server.getBaseUrl() + "sites/test/_api/lists?next=1";
            // Registered by path only, so it answers the initial request and every "?next=1"
            // follow-up identically - exactly how a server that never advances would behave.
            server.onPathStatus("/sites/test/_api/lists", 200, "application/xml",
                    listsFeed(selfUrl, "11111111-1111-1111-1111-111111111111", "List A"));

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetLists2013 api = new GetLists2013(httpClient, server.getBaseUrl() + "sites/test/", null);
                final List<GetListsResponse.SharePointList> lists = api.execute().getLists();

                assertEquals("the loop must stop at its page bound, one list per page", 100, lists.size());
            }
        }
    }

    private static String listsFeed(final String nextLink, final String id, final String title) {
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
        xml.append("<d:EntityTypeName>GenericList</d:EntityTypeName>");
        xml.append("<d:NoCrawl m:type=\"Edm.Boolean\">false</d:NoCrawl>");
        xml.append("<d:Hidden m:type=\"Edm.Boolean\">false</d:Hidden>");
        xml.append("</m:properties></content></entry>");
        xml.append("</feed>");
        return xml.toString();
    }
}
