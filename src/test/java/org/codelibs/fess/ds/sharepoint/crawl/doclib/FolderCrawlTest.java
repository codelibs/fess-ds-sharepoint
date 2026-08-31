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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true,
                    FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null)
                            .doCrawl(null, crawlingQueue);
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
                new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true,
                        FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH,
                        urlFilter).doCrawl(null, crawlingQueue);

                assertEquals("an excluded file must not be queued for further crawling", 0, crawlingQueue.size());
                // The folder lookup, the (empty) subfolder page and the file page - no getListItem,
                // getListItemValue, getListItemRole or getForms request for the excluded file.
                assertEquals("an excluded file must not cost its four follow-up requests", 3, server.getRecordedRequests().size());
            }
        }
    }

    /**
     * A file whose name contains the two characters
     * <a href="https://github.com/codelibs/fess-ds-sharepoint/issues/4">issue #4</a> is about must
     * still be indexed under a link that leads back to it.
     *
     * <p>The link is a query string, and the file's path is one of its parameter values. Against
     * the unfixed code that value is interpolated raw, so a {@code #} in a file name starts a URL
     * fragment: a browser never sends anything from there on, which drops the rest of the file
     * name and the whole {@code parent} parameter with it, and the search result opens the
     * library instead of the file. A {@code %} is read as the start of an escape sequence: a name
     * holding {@code %20} would reach SharePoint as one holding a space, and a {@code %} followed
     * by anything that is not two hex digits is not a valid escape at all. The {@code &amp;} and
     * the {@code +} are in the name for the same reason: they are not what the issue reports, but
     * they break the same value the same way.
     *
     * <p>The {@code %} also has to be escaped before the other three, or the {@code %23} produced
     * for the {@code #} would be escaped a second time in turn. That is why the expected value
     * below pins {@code %231} rather than {@code %25231}.
     *
     * <p>Only the characters that decide how a query string parses are escaped, so the link of
     * every file whose name holds none of them - which is nearly all of them - keeps the exact
     * bytes it has today. That is why the space below is expected to survive unescaped: it is
     * carried by the browser, not interpreted by it, and escaping it would change the indexed URL,
     * and therefore the document id, of every file already indexed with a space in its name.
     */
    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aFileNameHoldingQueryStringMetacharactersIsIndexedUnderAWorkingLink() throws Exception {
        final String listId = "33333333-3333-3333-3333-333333333333";
        final String fileName = "50% #1 R&D+notes.docx";
        final String fileUrl = FOLDER_URL + "/" + fileName;
        final String encodedFileUrl = FOLDER_URL + "/50%25%20%231%20R%26D%2Bnotes.docx";
        final String formsApi = "/sites/test/_api/Web/Lists(guid'" + listId + "')/Forms";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath(FOLDER_API, "application/json", "fixtures/modern/doclib_folder.json");
            server.onPathStatus(FOLDER_API + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(FOLDER_API + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"" + fileName + "\", \"ServerRelativeUrl\": \"" + fileUrl + "\"}]}");
            server.onPathStatus("/sites/test/_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodedFileUrl + "')/ListItemAllFields",
                    200, "application/json", "{\"Id\":\"201\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(201)\"}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(201)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus(formsApi, 200, "application/json",
                    "{\"value\":[{\"Id\":\"f1\",\"ServerRelativeUrl\":\"/sites/test/Lists/Docs/DispForm.aspx\",\"FormType\":4}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true,
                        FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null)
                                .doCrawl(null, crawlingQueue);

                assertEquals("the file must be queued for crawling", 1, crawlingQueue.size());
                final FileCrawl fileCrawl = (FileCrawl) crawlingQueue.poll();
                final String expected = client.getUrl() + "sites/test/Lists/Docs/AllItems.aspx?id=" + FOLDER_URL
                        + "/50%25 %231 R%26D%2Bnotes.docx" + "&parent=" + URLEncoder.encode(FOLDER_URL, StandardCharsets.UTF_8);
                assertEquals("every query string metacharacter must be escaped once, and nothing else about the link may change", expected,
                        fileCrawl.getWebUrl());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_formsAreFetchedOnceForAllFilesInTheSameList() throws Exception {
        // getForms() depends only on the list, but used to be re-fetched once per file. Two
        // files in the same list must cost exactly one Forms request between them, not two.
        final String listId = "22222222-2222-2222-2222-222222222222";
        final String file1 = FOLDER_URL + "/file1.docx";
        final String file2 = FOLDER_URL + "/file2.docx";
        final String listItemApiPrefix = "/sites/test/_api/Web/GetFolderByServerRelativePath(decodedurl='";
        final String formsApi = "/sites/test/_api/Web/Lists(guid'" + listId + "')/Forms";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath(FOLDER_API, "application/json", "fixtures/modern/doclib_folder.json");
            server.onPathStatus(FOLDER_API + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(FOLDER_API + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"file1.docx\", \"ServerRelativeUrl\": \"" + file1 + "\"},"
                            + "{\"Name\": \"file2.docx\", \"ServerRelativeUrl\": \"" + file2 + "\"}]}");
            server.onPathStatus(listItemApiPrefix + file1 + "')/ListItemAllFields", 200, "application/json",
                    "{\"Id\":\"101\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(101)\"}");
            server.onPathStatus(listItemApiPrefix + file2 + "')/ListItemAllFields", 200, "application/json",
                    "{\"Id\":\"102\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(102)\"}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(101)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(102)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus(formsApi, 200, "application/json",
                    "{\"value\":[{\"Id\":\"f1\",\"ServerRelativeUrl\":\"/sites/test/Lists/Docs/DispForm.aspx\",\"FormType\":4}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new FolderCrawl(client, FOLDER_URL, true, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true,
                        FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null)
                                .doCrawl(null, crawlingQueue);

                assertEquals("both files must still be queued for further crawling", 2, crawlingQueue.size());
                assertEquals("two files sharing one list must cost exactly one Forms request", 1,
                        server.getRecordedRequests().stream().filter(request -> formsApi.equals(request.getPath())).count());
            }
        }
    }
}
