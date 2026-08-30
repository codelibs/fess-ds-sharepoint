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
package org.codelibs.fess.ds.sharepoint.crawl.doclib;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SimpleUrlFilter;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Pins the page bound of the document library folder listing.
 */
public class FolderCrawlTest extends UnitDsTestCase {

    private static final String FOLDER_URL = "/sites/test/docs";

    /** What the folder listing asks for per request. */
    private static final int PAGE_SIZE = 100;

    /** How many pages one listing is allowed to fetch. */
    private static final int MAX_PAGES = 100;

    private static final String FOLDER_API = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='" + FOLDER_URL + "')";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    private static SharePointMockServer folderServer(final String foldersFixture) {
        final SharePointMockServer server = new SharePointMockServer();
        server.onPath(FOLDER_API, "application/json", "fixtures/modern/doclib_folder.json");
        // A stub registered by path answers every query identically, so this mock ignores $skip
        // exactly the way a broken SharePoint server does.
        server.onPath(FOLDER_API + "/Folders", "application/json", foldersFixture);
        server.onPath(FOLDER_API + "/Files", "application/json", "fixtures/modern/doclib_files_empty.json");
        return server;
    }

    private static int crawlAndCountQueued(final SharePointMockServer server) throws Exception {
        try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
            final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
            new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME,
                    FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);
            return crawlingQueue.size();
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_folderListingStopsWhenTheServerIgnoresSkip() throws Exception {
        try (SharePointMockServer server = folderServer("fixtures/modern/doclib_folders_full_page.json")) {
            server.start();

            // Every request gets the same full page back, so the only thing that can end the
            // listing is its page bound. Without one this call never returns, which is what the
            // timeout above turns into a failure instead of a hung suite.
            final int queued = crawlAndCountQueued(server);

            assertEquals("the listing must stop after its page bound", PAGE_SIZE * MAX_PAGES, queued);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_folderListingStopsOnAPageShorterThanRequested() throws Exception {
        try (SharePointMockServer server = folderServer("fixtures/modern/doclib_folders_short_page.json")) {
            server.start();

            final int queued = crawlAndCountQueued(server);

            assertEquals("a page shorter than the requested size is the last one", 2, queued);
            // One folder lookup, one page of subfolders, one page of files - no follow-up request
            // asking for a page the server already showed cannot exist.
            assertEquals("a short page must not cost another round trip", 3, server.getRecordedRequests().size());
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_excludePatternSkipsAFileBeforeTheFourFollowUpRequests() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath(FOLDER_API, "application/json", "fixtures/modern/doclib_folder.json");
            server.onPathStatus(FOLDER_API + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(FOLDER_API + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"report.pdf\", \"ServerRelativeUrl\": \"" + FOLDER_URL + "/report.pdf\"}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                final SimpleUrlFilter urlFilter = new SimpleUrlFilter(null, ".*report\\.pdf");
                new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME,
                        FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, urlFilter).doCrawl(null,
                                crawlingQueue);

                assertEquals("an excluded file must not be queued for further crawling", 0, crawlingQueue.size());
                // The folder lookup, the (empty) subfolder page and the file page - no getListItem,
                // getListItemValue, getListItemRole or getForms request for the excluded file.
                assertEquals("an excluded file must not cost its four follow-up requests", 3, server.getRecordedRequests().size());
            }
        }
    }
}
