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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

/**
 * Pins the date handling of the SharePoint 2013 {@code FieldValuesAsText} response, which
 * SharePoint renders in the site's regional format rather than a fixed one.
 */
public class GetListItemValue2013Test extends UnitDsTestCase {

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ITEM_PATH = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items(1)/FieldValuesAsText";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    private static String entry(final String modified, final String created) {
        final StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">");
        xml.append("<content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">");
        xml.append("<d:ID>1</d:ID><d:Title>Quarterly report</d:Title>");
        if (modified != null) {
            xml.append("<d:Modified>").append(modified).append("</d:Modified>");
        }
        if (created != null) {
            xml.append("<d:Created>").append(created).append("</d:Created>");
        }
        xml.append("<d:FileRef>/sites/test/Lists/Tasks/1_.000</d:FileRef>");
        xml.append("<d:Order>1</d:Order>");
        xml.append("</m:properties></content></entry>");
        return xml.toString();
    }

    @Test
    public void test_itemSurvivesADateItCannotParse() throws Exception {
        // FieldValuesAsText renders dates in the site's regional format. A site whose format does
        // not match the hard-coded MM/dd/yyyy HH:mm pattern used to lose the entire item - title,
        // content and permissions included - not just its dates.
        try (SharePointMockServer server = new SharePointMockServer()) {
            // ISO 8601 rather than MM/dd/yyyy HH:mm: the '-' and 'T' literals cannot match the
            // expected pattern's '/' and ' ' separators, so this fails outright.
            server.onPathStatus(ITEM_PATH, 200, "application/xml", entry("2026-08-23T10:00:00Z", "2026-08-23T09:00:00Z"));
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListItemValueResponse response =
                        new GetListItemValue2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                .setItemId("1")
                                .execute();

                assertNotNull("the item must still be returned", response);
                assertEquals("the item's title must survive", "Quarterly report", response.getTitle());
                assertNull("an unparseable date is dropped, not fatal", response.getModified());
                assertNull("an unparseable date is dropped, not fatal", response.getCreated());
            }
        }
    }

    @Test
    public void test_itemSurvivesAMissingDate() throws Exception {
        // DocumentUtil.getValue returns null when a key is absent, and SimpleDateFormat.parse(null)
        // throws a NullPointerException - a missing date field used to be just as fatal as an
        // unparseable one.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ITEM_PATH, 200, "application/xml", entry(null, null));
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListItemValueResponse response =
                        new GetListItemValue2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                .setItemId("1")
                                .execute();

                assertNotNull("the item must still be returned", response);
                assertEquals("the item's title must survive", "Quarterly report", response.getTitle());
                assertNull("a missing date must not throw", response.getModified());
                assertNull("a missing date must not throw", response.getCreated());
            }
        }
    }

    @Test
    public void test_parsesADateInTheExpectedFormat() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(ITEM_PATH, 200, "application/xml", entry("08/23/2026 10:00", "08/23/2026 09:00"));
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetListItemValueResponse response =
                        new GetListItemValue2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                .setItemId("1")
                                .execute();

                assertNotNull("Modified must parse when it matches the expected format", response.getModified());
                assertNotNull("Created must parse when it matches the expected format", response.getCreated());
            }
        }
    }
}
