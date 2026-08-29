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
}
