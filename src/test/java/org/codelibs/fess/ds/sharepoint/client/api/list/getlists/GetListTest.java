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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class GetListTest extends UnitDsTestCase {

    private static final String JSON = "application/json";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_execute_byListName_doublesApostropheInGetByTitleLiteral() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            // SharePointClient.buildSiteUrl always returns a URL ending in "/", and every API
            // class does siteUrl + "/" + apiPath, so the path built below has a redundant empty
            // segment ("/sites/test//_api/..."). That empty segment is what makes Apache
            // HttpClient's ProtocolExec rewrite the URI (URIUtils.rewriteURI with
            // DROP_FRAGMENT_AND_NORMALIZE): it decodes the path into segments and re-encodes them
            // with HttpClient's own PATHSAFE set, in which an apostrophe is unreserved (so it
            // comes back out literal) but a space is not (so %20 survives). That is the actual
            // reason the doubled-then-encoded value this test feeds in ("O%27%27Brien%27%27s
            // List") reaches the mock server below as a doubled *literal* apostrophe
            // ("O''Brien''s%20List") rather than staying "%27%27". If a future cleanup removes the
            // redundant "/" (siteUrl already ends in one), this normalization stops happening and
            // the literal below would need to become "%27%27" again - either form is the doubled
            // form, and what this test actually verifies is that doubling happens before encoding,
            // so a server never sees a lone apostrophe no matter which way the normalization goes.
            server.onPathStatus("/sites/test/_api/web/lists/GetByTitle('O''Brien''s%20List')", 200, JSON,
                    "{\"Title\":\"O'Brien's List\",\"Id\":\"11111111-1111-1111-1111-111111111111\",\"EntityTypeName\":\"OBriens_x0027_s_x0020_List\"}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListResponse response =
                        new GetList(httpClient, server.getBaseUrl() + "sites/test/", null).setListName("O'Brien's List").execute();
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
    public void test_execute_byListId_acceptsAWellFormedGuid() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/lists(guid'11111111-1111-1111-1111-111111111111')", 200, JSON,
                    "{\"Title\":\"Shared Documents\",\"Id\":\"11111111-1111-1111-1111-111111111111\",\"EntityTypeName\":\"Shared_x0020_Documents\"}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListResponse response =
                        new GetList(httpClient, server.getBaseUrl() + "sites/test/", null).setListId("11111111-1111-1111-1111-111111111111")
                                .execute();
                assertEquals("Shared Documents", response.getList().getListName());
            }

            assertEquals(1, server.getRecordedRequests().size());
        }
    }

    @Test
    public void test_execute_byListId_rejectsAValueThatIsNotAGuid() {
        // site.list_id is a data-config parameter a user can set to anything. It is interpolated
        // into an OData guid'...' literal with no encoding at all, so a value that is not
        // actually a GUID - in particular one containing an apostrophe - must be rejected before
        // it ever reaches the request.
        final GetList getList = new GetList(null, "https://example.sharepoint.com/sites/test", null);
        getList.setListId("11111111-1111-1111-1111-111111111111' or 1=1--");

        assertThrows(ValidationException.class, getList::execute,
                "a listId that is not a GUID must be rejected instead of reaching the request");
    }
}
