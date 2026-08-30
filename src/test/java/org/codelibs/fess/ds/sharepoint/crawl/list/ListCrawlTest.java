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
package org.codelibs.fess.ds.sharepoint.crawl.list;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Pins the page bound of the list item listing, the plugin's primary crawl path.
 *
 * <p>The listing ends when a page comes back empty. Nothing else ends it, so a server that
 * answers the same first page to every paging token kept the loop offering the same items to the
 * crawling queue forever.
 */
public class ListCrawlTest extends UnitDsTestCase {

    private static final String SITE_NAME = "test";

    private static final String JSON = "application/json";

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    /** How many pages one listing is allowed to fetch. */
    private static final int MAX_PAGES = 10000;

    /** Items per request, small enough to keep the bound cheap to reach. */
    private static final int NUMBER_PER_PAGE = 2;

    private static final String LIST_API = "/sites/test/_api/web/lists(guid'" + LIST_ID + "')";

    private static final String ITEMS_API = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_listItemListingStopsWhenTheServerIgnoresThePagingToken() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON,
                    "{\"Title\":\"Announcements\",\"Id\":\"" + LIST_ID + "\",\"EntityTypeName\":\"AnnouncementsList\"}");
            // A stub registered by path answers every query identically, so this mock ignores the
            // paging token exactly the way a broken SharePoint server does. Only the page bound
            // can end the listing; without one this call never returns, which is what the timeout
            // above turns into a failure instead of a hung suite.
            server.onPathStatus(ITEMS_API, 200, JSON, itemPage(NUMBER_PER_PAGE));
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("the listing must stop after its page bound", MAX_PAGES,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
                assertEquals("every item of every page it did read must be queued", MAX_PAGES * NUMBER_PER_PAGE, crawlingQueue.size());
            }
        }
    }

    /** A page of {@code count} items with no attachments, so nothing else is queued for them. */
    private static String itemPage(final int count) {
        final StringBuilder buf = new StringBuilder("{\"value\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                buf.append(',');
            }
            buf.append("{\"Id\":\"")
                    .append(i)
                    .append("\",\"Title\":\"Item ")
                    .append(i)
                    .append("\",\"Attachments\":false,\"odata.editLink\":\"Web/Lists(guid'")
                    .append(LIST_ID)
                    .append("')/Items(")
                    .append(i)
                    .append(")\",\"Created\":\"2026-01-01T00:00:00Z\",\"Modified\":\"2026-01-02T00:00:00Z\"}");
        }
        return buf.append("]}").toString();
    }
}
