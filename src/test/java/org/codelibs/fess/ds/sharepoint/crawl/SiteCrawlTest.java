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
package org.codelibs.fess.ds.sharepoint.crawl;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.ds.sharepoint.SharePointCrawler;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Pins the page bound of the top-level folder listing at the start of a site crawl.
 *
 * <p>{@code GetFolders} always sends an explicit {@code $top=100}, so a site with more than one
 * page of top-level folders was silently truncated at 100 - not by a server default, but by the
 * plugin's own request never asking for a second page.
 */
public class SiteCrawlTest extends UnitDsTestCase {

    private static final String SITE_NAME = "test";

    /** What the top-level folder listing asks for per request. */
    private static final int PAGE_SIZE = 100;

    /** How many pages one listing is allowed to fetch. */
    private static final int MAX_PAGES = 100;

    private static final String FOLDER_API = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='/sites/test/')/Folders";

    private static final String LISTS_API = "/sites/test/_api/lists";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    private static SharePointMockServer siteServer(final String foldersFixture) {
        final SharePointMockServer server = new SharePointMockServer();
        // A stub registered by path answers every query identically, so this mock ignores $skip
        // exactly the way a broken SharePoint server does.
        server.onPath(FOLDER_API, "application/json", foldersFixture);
        server.onPathStatus(LISTS_API, 200, "application/json", "{\"value\":[]}");
        return server;
    }

    private static int crawlAndCountQueued(final SharePointMockServer server) throws Exception {
        try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
            final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
            config.setSiteName(SITE_NAME);
            final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
            new SiteCrawl(client, config, new ConcurrentHashMap<>()).doCrawl(null, crawlingQueue);
            return crawlingQueue.size();
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_topLevelFolderListingStopsWhenTheServerIgnoresSkip() throws Exception {
        try (SharePointMockServer server = siteServer("fixtures/modern/doclib_folders_full_page.json")) {
            server.start();

            // Every request gets the same full page back, so the only thing that can end the
            // listing is its page bound. Without one this call never returns, which is what the
            // timeout above turns into a failure instead of a hung suite.
            final int queued = crawlAndCountQueued(server);

            // Every page's folders, plus the one unconditional "Shared Documents" FolderCrawl
            // SiteCrawl always queues at the end.
            assertEquals("the listing must stop after its page bound", PAGE_SIZE * MAX_PAGES + 1, queued);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_topLevelFolderListingReadsASecondPageAndStopsOnAShortOne() throws Exception {
        // A single short page proves nothing here: the unpaged call this listing used to make
        // asked the same server for the same first page and got the same answer, so a one-page
        // fixture reads identically before and after. The listing has to be made to reach for a
        // page that only a paged request can get.
        try (SharePointMockServer server = siteServer("fixtures/modern/doclib_folders_full_page.json")) {
            server.onPathQuery(FOLDER_API, "%24skip=100&%24top=100", "application/json",
                    SharePointMockServer.readClasspath("fixtures/modern/doclib_folders_short_page.json"));
            server.start();

            final int queued = crawlAndCountQueued(server);

            // Both pages' folders, plus the unconditional "Shared Documents" entry. An unpaged
            // listing stops after the first page and reaches PAGE_SIZE + 1.
            assertEquals("the second page must be read too", PAGE_SIZE + 2 + 1, queued);
            // Two pages of top-level folders, one call for the site's lists - no third folder
            // request asking for a page the short one already showed cannot exist.
            assertEquals("a page shorter than the requested size is the last one", 3, server.getRecordedRequests().size());
        }
    }
}
