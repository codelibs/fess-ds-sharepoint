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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class GetList2013Test extends UnitDsTestCase {

    private static final String XML = "application/xml";

    private static final String ENTRY_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<entry xmlns=\"http://www.w3.org/2005/Atom\"\n"
            + "       xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\"\n"
            + "       xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">\n"
            + "  <content type=\"application/xml\">\n" + "    <m:properties>\n"
            + "      <d:Id m:type=\"Edm.Guid\">11111111-1111-1111-1111-111111111111</d:Id>\n" + "      <d:Title>O'Brien's List</d:Title>\n"
            + "      <d:EntityTypeName>OBriens_x0027_s_x0020_List</d:EntityTypeName>\n" + "    </m:properties>\n" + "  </content>\n"
            + "</entry>\n";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_execute_byListName_doublesApostropheInGetByTitleLiteral() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            // See GetListTest for why the redundant "/" in siteUrl + "/" + apiPath makes Apache
            // HttpClient's URI normalization turn the doubled-then-encoded value ("%27%27") into a
            // doubled literal apostrophe on the wire. Either form is the doubled form; what has to
            // hold regardless is that doubling happened before encoding, so a server never sees a
            // lone apostrophe.
            server.onPathStatus("/sites/test/_api/web/lists/GetByTitle('O''Brien''s%20List')", 200, XML, ENTRY_XML);
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListResponse response =
                        new GetList2013(httpClient, server.getBaseUrl() + "sites/test/", null).setListName("O'Brien's List").execute();
                assertEquals("O'Brien's List", response.getList().getListName());
            }

            assertEquals(1, server.getRecordedRequests().size());
            final SharePointMockServer.RecordedRequest recorded = server.getRecordedRequests().get(0);
            assertEquals(
                    "apostrophes in the list name must be doubled before encoding - '%27%27' and a "
                            + "normalized literal doubled apostrophe are both the doubled form, a server must never see just one",
                    "/sites/test/_api/web/lists/GetByTitle('O''Brien''s%20List')", recorded.getPath());
        }
    }

    @Test
    public void test_execute_byListId_rejectsAValueThatIsNotAGuid() {
        final GetList2013 getList = new GetList2013(null, "https://example.sharepoint.com/sites/test", null);
        getList.setListId("11111111-1111-1111-1111-111111111111' or 1=1--");

        assertThrows(ValidationException.class, getList::execute,
                "a listId that is not a GUID must be rejected instead of reaching the request");
    }
}
