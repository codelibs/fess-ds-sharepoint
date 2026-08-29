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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistforms;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

public class GetForms2013Test extends UnitDsTestCase {

    private static final String XML = "application/xml";

    private static final String EMPTY_FEED_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>\n";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_execute_byListName_doublesApostropheInGetByTitleLiteral() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            // Same fix as the modern GetForms: the raw listName used to go straight into the URL
            // with no encoding at all. See GetListTest for why the redundant "/" in
            // siteUrl + "/" + apiPath makes Apache HttpClient's URI normalization turn the
            // doubled-then-encoded value ("%27%27") into a doubled literal apostrophe on the wire.
            server.onPathStatus("/sites/test/_api/lists/getbytitle('O''Brien''s%20List')/Forms", 200, XML, EMPTY_FEED_XML);
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetForms2013Response response =
                        new GetForms2013(httpClient, server.getBaseUrl() + "sites/test/", null).setListName("O'Brien's List").execute();
                assertEquals(0, response.getForms().size());
            }

            assertEquals(1, server.getRecordedRequests().size());
            final SharePointMockServer.RecordedRequest recorded = server.getRecordedRequests().get(0);
            assertEquals(
                    "apostrophes in the list name must be doubled before encoding - '%27%27' and a "
                            + "normalized literal doubled apostrophe are both the doubled form, a server must never see just one",
                    "/sites/test/_api/lists/getbytitle('O''Brien''s%20List')/Forms", recorded.getPath());
        }
    }
}
