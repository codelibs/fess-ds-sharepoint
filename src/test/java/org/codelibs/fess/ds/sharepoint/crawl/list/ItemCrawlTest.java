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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SimpleUrlFilter;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Exercises {@code include_pattern}/{@code exclude_pattern} on the list item path, where the
 * URL-ish value (FileRef) only exists after {@code getListItemValue} has already been requested -
 * unlike a file, whose server-relative URL is known before any request is made (see
 * {@code FolderCrawlTest}).
 */
public class ItemCrawlTest extends UnitDsTestCase {

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ITEM_PATH = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items(1)/FieldValuesAsText";
    private static final String FILE_REF = "/sites/test/Lists/Tasks/1_.000";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        final FileTypeHelper fileTypeHelper = new FileTypeHelper();
        fileTypeHelper.init();
        ComponentUtil.register(fileTypeHelper, "fileTypeHelper");
    }

    private static ItemCrawl itemCrawl(final SharePointClient client, final SimpleUrlFilter urlFilter) {
        return new ItemCrawl(client, LIST_ID, "Tasks", "1", null, null, List.of(), true, List.of(), List.of(), urlFilter);
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_excludePatternSkipsAnItemOnlyAfterFetchingItsValue() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ITEM_PATH, 200, "application/json",
                    "{\"ID\":\"1\",\"Title\":\"Quarterly report\",\"FileRef\":\"" + FILE_REF + "\"}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final ItemCrawl itemCrawl = itemCrawl(client, new SimpleUrlFilter(null, ".*Tasks.*"));
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = itemCrawl.doCrawl(null, crawlingQueue);

                assertNull("an item matching exclude_pattern must be skipped", dataMap);
                assertEquals("the value must still have been fetched before the item could be filtered", 1,
                        server.getRecordedRequests().size());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_itemNotMatchingExcludePatternIsCrawled() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ITEM_PATH, 200, "application/json",
                    "{\"ID\":\"1\",\"Title\":\"Quarterly report\",\"FileRef\":\"" + FILE_REF + "\"}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final ItemCrawl itemCrawl = itemCrawl(client, new SimpleUrlFilter(null, ".*DoesNotMatch.*"));
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = itemCrawl.doCrawl(null, crawlingQueue);

                assertNotNull("an item not matching exclude_pattern must still be crawled", dataMap);
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_noFilterConfiguredCrawlsTheItem() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ITEM_PATH, 200, "application/json",
                    "{\"ID\":\"1\",\"Title\":\"Quarterly report\",\"FileRef\":\"" + FILE_REF + "\"}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final ItemCrawl itemCrawl = itemCrawl(client, null);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = itemCrawl.doCrawl(null, crawlingQueue);

                assertNotNull("with no filter built (neither pattern configured), an item must still be crawled", dataMap);
            }
        }
    }
}
