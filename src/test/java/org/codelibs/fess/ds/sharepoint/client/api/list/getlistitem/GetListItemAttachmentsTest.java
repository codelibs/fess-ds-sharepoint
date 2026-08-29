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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Pins the paging behavior of the attachment files listing.
 *
 * <p>{@code GetListItemAttachments} used to issue a single unpaged request, silently truncating
 * an item with more attachments than the server's own default page size.
 */
public class GetListItemAttachmentsTest extends UnitDsTestCase {

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ITEM_PATH = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items(1)/AttachmentFiles";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_followsODataNextLinkAcrossPages() throws Exception {
        // A response that includes odata.nextLink used to be a dead end: only the first page's
        // attachments were ever read.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String nextUrl = server.getBaseUrl() + ITEM_PATH.substring(1) + "?nextpage=1";
            server.onPathStatus(ITEM_PATH, 200, "application/json",
                    "{\"value\":[{\"FileName\":\"a.txt\",\"ServerRelativeUrl\":\"/sites/test/a.txt\"}]," + "\"odata.nextLink\":\"" + nextUrl
                            + "\"}");
            server.onPathQuery(ITEM_PATH, "nextpage=1", "application/json",
                    "{\"value\":[{\"FileName\":\"b.txt\",\"ServerRelativeUrl\":\"/sites/test/b.txt\"}]}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListItemAttachmentsResponse response =
                        new GetListItemAttachments(httpClient, server.getBaseUrl() + "sites/test", null).setId(LIST_ID, "1").execute();

                final List<GetListItemAttachmentsResponse.AttachmentFile> files = response.getFiles();
                assertEquals("both pages must be read", 2, files.size());
                assertEquals("a.txt", files.get(0).getFileName());
                assertEquals("b.txt", files.get(1).getFileName());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_stopsFollowingNextLinkAfterPageBound() throws Exception {
        // A server that always answers with a nextLink pointing back at itself must not be
        // followed forever. Without a bound this call never returns, which is what the timeout
        // above turns into a failure instead of a hung suite.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();
            final String selfUrl = server.getBaseUrl() + ITEM_PATH.substring(1) + "?next=1";
            server.onPathStatus(ITEM_PATH, 200, "application/json",
                    "{\"value\":[{\"FileName\":\"a.txt\",\"ServerRelativeUrl\":\"/sites/test/a.txt\"}]," + "\"odata.nextLink\":\"" + selfUrl
                            + "\"}");

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListItemAttachmentsResponse response =
                        new GetListItemAttachments(httpClient, server.getBaseUrl() + "sites/test", null).setId(LIST_ID, "1").execute();

                assertEquals("the loop must stop at its page bound, one file per page", 100, response.getFiles().size());
            }
        }
    }
}
