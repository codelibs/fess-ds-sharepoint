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

import java.util.Collections;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.crawler.filter.UrlFilter;
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
 * Pins that include_pattern/exclude_pattern reaches a list item's attachments.
 *
 * <p>An attachment has a server-relative URL of its own, distinct from the {@code FileRef} of the
 * item it hangs off, so excluding the item does not exclude its attachments. Before this filter was
 * threaded through, an attachment was downloaded, extracted and indexed no matter what either
 * pattern said.
 */
public class ItemAttachmentsCrawlTest extends UnitDsTestCase {

    private static final String JSON = "application/json";

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ATTACHMENTS_API = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items(1)/AttachmentFiles";

    private static final String FORMS_API = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Forms";

    private static final String KEPT_URL = "/sites/test/Lists/Tasks/Attachments/1/keep.txt";

    private static final String EXCLUDED_URL = "/sites/test/Lists/Tasks/Attachments/1/drop.txt";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_anExcludedAttachmentIsNotQueued() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ATTACHMENTS_API, 200, JSON, "{\"value\":[{\"FileName\":\"keep.txt\",\"ServerRelativeUrl\":\"" + KEPT_URL
                    + "\"},{\"FileName\":\"drop.txt\",\"ServerRelativeUrl\":\"" + EXCLUDED_URL + "\"}]}");
            server.onPathStatus(FORMS_API, 200, JSON,
                    "{\"value\":[{\"ServerRelativeUrl\":\"/sites/test/Lists/Tasks/DispForm.aspx\",\"FormType\":4}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final UrlFilter urlFilter = new SimpleUrlFilter(null, ".*/drop\\.txt");
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                crawlWith(client, urlFilter).doCrawl(null, crawlingQueue);

                assertEquals("only the attachment the filter allows may be queued", 1, crawlingQueue.size());
                assertEquals("and it must be the allowed one", "file#" + KEPT_URL, crawlingQueue.peek().getStatsKey().getId());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_everyAttachmentIsQueuedWithoutAFilter() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ATTACHMENTS_API, 200, JSON, "{\"value\":[{\"FileName\":\"keep.txt\",\"ServerRelativeUrl\":\"" + KEPT_URL
                    + "\"},{\"FileName\":\"drop.txt\",\"ServerRelativeUrl\":\"" + EXCLUDED_URL + "\"}]}");
            server.onPathStatus(FORMS_API, 200, JSON,
                    "{\"value\":[{\"ServerRelativeUrl\":\"/sites/test/Lists/Tasks/DispForm.aspx\",\"FormType\":4}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                crawlWith(client, null).doCrawl(null, crawlingQueue);

                assertEquals("neither pattern configured must leave every attachment crawled", 2, crawlingQueue.size());
            }
        }
    }

    private static ItemAttachmentsCrawl crawlWith(final SharePointClient client, final UrlFilter urlFilter) {
        return new ItemAttachmentsCrawl(client, LIST_ID, "Tasks", "1", new Date(), new Date(), Collections.emptyList(), false,
                FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH,
                new ConcurrentHashMap<>(), urlFilter);
    }
}
