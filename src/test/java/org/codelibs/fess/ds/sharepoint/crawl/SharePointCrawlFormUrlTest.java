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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ItemAttachmentsCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * The display form URL is appended to the site URL as the web link of a list item, a library
 * file and an attachment. SharePoint reports it decoded, so a list whose name holds a space
 * must have the space written as {@code %20} before it is indexed: Fess redirects a click to
 * the indexed {@code url}, and a raw space reaches the browser as {@code +}, which the server
 * reads as a literal plus sign and answers 404.
 */
public class SharePointCrawlFormUrlTest extends UnitDsTestCase {

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String FORMS_API = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Forms";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aSpaceInTheListNameIsWrittenAsPercent20() throws Exception {
        assertEquals("/sites/test/Lists/Blank%20list/DispForm.aspx", displayFormUrl("/sites/test/Lists/Blank list/DispForm.aspx"));
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aNameWithoutASpaceKeepsItsUrl() throws Exception {
        assertEquals("/sites/test/Lists/Tasks/DispForm.aspx", displayFormUrl("/sites/test/Lists/Tasks/DispForm.aspx"));
        assertEquals("/sites/test/Lists/a+b/DispForm.aspx", displayFormUrl("/sites/test/Lists/a+b/DispForm.aspx"));
    }

    private static String displayFormUrl(final String serverRelativeUrl) throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FORMS_API, 200, "application/json",
                    "{\"value\":[{\"ServerRelativeUrl\":\"" + serverRelativeUrl + "\",\"FormType\":4}]}");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final SharePointCrawl crawl = new ItemAttachmentsCrawl(client, LIST_ID, "Tasks", "1", new Date(), new Date(),
                        Collections.emptyList(), false, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, new ConcurrentHashMap<>(), null);
                return crawl.getDisplayFormUrl(LIST_ID, new HashMap<>());
            }
        }
    }
}
