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
package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

public class GetFileTest extends UnitDsTestCase {

    private static final String FILE_URL = "/sites/test/docs/a.txt";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_anErrorResponseReportsItsStatusAndBody() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value", 403,
                    "application/json", "{\"error\":{\"message\":\"Access denied.\"}}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                // The exception describing the failure used to be caught by this method's own
                // generic handler one line later and wrapped in a second one, which left the
                // caller with nothing but "GetFile Request failure.".
                final SharePointClientException e =
                        assertThrows(SharePointClientException.class, getFile::execute, "an error response must fail the request");

                assertTrue("the status code must survive to the caller", e.getMessage().contains("status:403"));
                assertTrue("the server's explanation must survive to the caller", e.getMessage().contains("Access denied."));
            }
        }
    }
}
