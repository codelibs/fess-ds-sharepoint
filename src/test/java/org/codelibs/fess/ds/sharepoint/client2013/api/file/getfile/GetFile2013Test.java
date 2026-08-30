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
package org.codelibs.fess.ds.sharepoint.client2013.api.file.getfile;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetFile2013Test extends UnitDsTestCase {

    private static final String FILE_URL = "/sites/test/docs/a.txt";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * The status code has to be readable from the exception itself, not only from the message:
     * SharePointCrawler#doCrawl's retry loop decides whether to back off by calling
     * getStatusCode(), and the SharePointClientException(String) constructor leaves that at -1
     * whatever the message says. Asserting on the message alone cannot tell the two constructors
     * apart, because the "status:403" substring is built at the call site either way.
     */
    @Test
    public void test_anErrorResponseReportsItsStatusAndBody() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativeUrl('" + FILE_URL + "')/$value", 403, "application/json",
                    "{\"error\":{\"message\":\"Access denied.\"}}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile2013 getFile =
                        new GetFile2013(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                // Same double wrapping as the modern GetFile had, in its own copy of the method.
                final SharePointClientException e =
                        assertThrows(SharePointClientException.class, getFile::execute, "an error response must fail the request");

                assertEquals("the status code must be readable from the exception, not just its message", 403, e.getStatusCode());
                assertTrue("the server's explanation must survive to the caller", e.getMessage().contains("Access denied."));
            }
        }
    }

    /**
     * The status code matters because of what the retry loop does with it, so this pins the case
     * that loop actually acts on rather than the 403 above, which it treats like any other error.
     */
    @Test
    public void test_a503CarriesItsStatusCodeForTheBackoff() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativeUrl('" + FILE_URL + "')/$value", 503, "text/html",
                    "<html><body>Service Unavailable</body></html>");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile2013 getFile =
                        new GetFile2013(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                final SharePointClientException e =
                        assertThrows(SharePointClientException.class, getFile::execute, "a 503 must fail the request");

                assertEquals("a 503 from a file download must carry its status code so the crawler can back off", 503, e.getStatusCode());
            }
        }
    }

    /**
     * Same bypass as the modern GetFile, in its own copy of execute() - so it needs its own call
     * to the busy-server wait too.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_backsOffWhenTheServerReportsItselfBusy() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String path = "/sites/test/_api/web/GetFileByServerRelativeUrl('" + FILE_URL + "')/$value";
            server.onPathStatus(path, 200, "text/plain", "file content");
            server.withHeader("X-SharePointHealthScore", "10");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<Long> recordedSleeps = new ArrayList<>();
                final SharePointBackoff backoff = new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
                final GetFile2013 getFile =
                        new GetFile2013(httpClient, server.getBaseUrl() + "sites/test", null, backoff).setServerRelativeUrl(FILE_URL);

                getFile.execute().close();

                assertEquals("a health score of 10 (attempt 1: 10 - threshold 8 - 1) must back off once", 1, recordedSleeps.size());
                assertEquals("attempt 1 must double the initial delay once", 4000L, recordedSleeps.get(0).longValue());
            }
        }
    }
}
