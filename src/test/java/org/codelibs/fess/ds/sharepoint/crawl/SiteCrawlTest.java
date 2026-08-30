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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;
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
            new SiteCrawl(client, config, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), null).doCrawl(null, crawlingQueue);
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

            // Every page's folders, plus the "Shared Documents" fallback FolderCrawl - none of
            // this fixture's folders are named "Shared Documents", so the fallback still fires.
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

            // Both pages' folders, plus the "Shared Documents" fallback entry. An unpaged
            // listing stops after the first page and reaches PAGE_SIZE + 1.
            assertEquals("the second page must be read too", PAGE_SIZE + 2 + 1, queued);
            // Two pages of top-level folders, one call for the site's lists - no third folder
            // request asking for a page the short one already showed cannot exist.
            assertEquals("a page shorter than the requested size is the last one", 3, server.getRecordedRequests().size());
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theSharedDocumentsFallbackIsSkippedWhenTheFolderListingAlreadyFoundIt() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            // The top-level folder listing itself reports a folder literally named "Shared
            // Documents" - its real, server-supplied name on an English-language site.
            server.onPathStatus(FOLDER_API, 200, "application/json",
                    "{\"value\": [{\"Name\": \"Shared Documents\", \"ServerRelativeUrl\": \"/sites/test/Shared Documents\"}]}");
            server.onPathStatus(LISTS_API, 200, "application/json", "{\"value\":[]}");
            server.start();

            final int queued = crawlAndCountQueued(server);

            // Exactly one FolderCrawl for the library - the fallback must not add a second one
            // for a document library the listing above already found.
            assertEquals("the fallback must be skipped when the listing already found Shared Documents", 1, queued);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theSharedDocumentsFallbackHonoursRoleSkip() throws Exception {
        // Today the fallback is built with a literal `false`, the only place in the crawl that
        // overrides role.skip. If that literal survives this change, the file below has its role
        // fetched even though config.setSkipRole(true) is set, and the RoleAssignments request -
        // deliberately left unstubbed - fails the crawl.
        // encodeRelativeUrl percent-encodes each path segment before it hits the wire, and
        // SharePointMockServer matches the raw wire form (see its javadoc), so a stub path must
        // spell the space as %20 even though the crawl's own server-relative URLs use a literal
        // space.
        final String docLibUrl = "/sites/test/Shared Documents";
        final String encodedDocLibUrl = "/sites/test/Shared%20Documents";
        final String docLibApi = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='" + encodedDocLibUrl + "')";
        final String fileUrl = docLibUrl + "/a.txt";
        final String encodedFileUrl = encodedDocLibUrl + "/a.txt";
        final String listId = "33333333-3333-3333-3333-333333333333";
        try (SharePointMockServer server = new SharePointMockServer()) {
            // No top-level folder named "Shared Documents", so the fallback fires.
            server.onPath(FOLDER_API, "application/json", "fixtures/modern/doclib_folders_short_page.json");
            server.onPathStatus(LISTS_API, 200, "application/json", "{\"value\":[]}");

            server.onPath(docLibApi, "application/json", "fixtures/modern/doclib_folder.json");
            server.onPathStatus(docLibApi + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(docLibApi + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"a.txt\", \"ServerRelativeUrl\": \"" + fileUrl + "\"}]}");
            server.onPathStatus("/sites/test/_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodedFileUrl + "')/ListItemAllFields",
                    200, "application/json", "{\"Id\":\"1\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(1)\"}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(1)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Forms", 200, "application/json",
                    "{\"value\":[{\"Id\":\"f1\",\"ServerRelativeUrl\":\"/sites/test/Lists/Docs/DispForm.aspx\",\"FormType\":4}]}");
            // Deliberately no stub for .../Items(1)/RoleAssignments: skipRole=true must mean it is
            // never requested.
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
                config.setSiteName(SITE_NAME);
                config.setSkipRole(true);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new SiteCrawl(client, config, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), null).doCrawl(null, crawlingQueue);

                final List<SharePointCrawl> queued = new ArrayList<>(crawlingQueue);
                assertEquals("only the fallback FolderCrawl for Shared Documents must be queued", 3, queued.size());
                final SharePointCrawl fallback = queued.get(queued.size() - 1);

                assertDoesNotThrow(() -> fallback.doCrawl(null, new ConcurrentLinkedQueue<>()),
                        "the fallback FolderCrawl must be built with config.isSkipRole(), not a literal false");
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theTopLevelFolderListingFollowsTheConfiguredSitePath() throws Exception {
        final String teamsFolderApi = "/teams/eng/_api/web/GetFolderByServerRelativePath(decodedUrl='/teams/eng/')/Folders";
        final String teamsListsApi = "/teams/eng/_api/lists";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(teamsFolderApi, 200, "application/json", "{\"value\": []}");
            server.onPathStatus(teamsListsApi, 200, "application/json", "{\"value\":[]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSitePath("/teams/eng").build()) {
                final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
                config.setSitePath("/teams/eng");
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new SiteCrawl(client, config, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), null).doCrawl(null, crawlingQueue);

                assertEquals("just the Shared Documents fallback for the configured site path", 1, crawlingQueue.size());
                assertEquals("the top-level folder listing must target the configured site path", 1,
                        server.getRecordedRequests().stream().filter(request -> teamsFolderApi.equals(request.getPath())).count());
            }
        }
    }
}
