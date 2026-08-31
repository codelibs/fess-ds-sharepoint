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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitems;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;

public class GetListItems2013Test extends UnitDsTestCase {

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ITEMS_PATH = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_utcTimestampsAreNotShiftedByTheJvmTimeZone() throws Exception {
        // The 'Z' in the pattern is a literal, so without an explicit zone the instant is read in
        // whatever zone the JVM happens to run in.
        final TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            try (SharePointMockServer server = new SharePointMockServer()) {
                final String feed = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                        + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                        + "<entry><link rel=\"edit\" href=\"Items(1)\"/><content><m:properties "
                        + "xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">" + "<d:Id>1</d:Id>"
                        + "<d:Title>Report</d:Title><d:Attachments m:type=\"Edm.Boolean\">false</d:Attachments>"
                        + "<d:Created>2026-01-01T00:00:00Z</d:Created><d:Modified>2026-01-02T00:00:00Z</d:Modified>"
                        + "</m:properties></content></entry></feed>";
                server.onPathStatus(ITEMS_PATH, 200, "application/xml", feed);
                server.start();

                try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                    final List<GetListItemsResponse.ListItem> items =
                            new GetListItems2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                    .execute()
                                    .getListItems();

                    assertEquals("exactly one item", 1, items.size());
                    assertEquals("Created must be read as UTC, not the JVM default zone",
                            Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), items.get(0).getCreated().getTime());
                    assertEquals("Modified must be read as UTC, not the JVM default zone",
                            Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), items.get(0).getModified().getTime());
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void test_itemWithAMalformedDateSurvivesAlongsideAGoodItem() throws Exception {
        // A malformed date used to throw out of the per-item forEach, which aborted every
        // remaining item on the same page - including this page's perfectly good first item.
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String feed = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                    + entry("1", "Good item", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z")
                    + entry("2", "Bad date item", "not-a-date", "not-a-date") + "</feed>";
            server.onPathStatus(ITEMS_PATH, 200, "application/xml", feed);
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<GetListItemsResponse.ListItem> items =
                        new GetListItems2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                .execute()
                                .getListItems();

                assertEquals("both items must be returned", 2, items.size());
                assertEquals("Good item", items.get(0).getTitle());
                assertNotNull("the good item's date must still parse", items.get(0).getCreated());
                assertEquals("Bad date item", items.get(1).getTitle());
                assertNull("an unparseable date is dropped, not fatal", items.get(1).getCreated());
                assertNull("an unparseable date is dropped, not fatal", items.get(1).getModified());
            }
        }
    }

    @Test
    public void test_itemSurvivesAMissingDate() throws Exception {
        // DocumentUtil.getValue returns null when a key is absent; a missing Created/Modified
        // element used to drop the whole item rather than just leave its date unset.
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String feed = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                    + entry("1", "No dates", null, null) + "</feed>";
            server.onPathStatus(ITEMS_PATH, 200, "application/xml", feed);
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<GetListItemsResponse.ListItem> items =
                        new GetListItems2013(httpClient, server.getBaseUrl() + "sites/test", null).setListId(LIST_ID)
                                .execute()
                                .getListItems();

                assertEquals("the item must still be returned", 1, items.size());
                assertEquals("No dates", items.get(0).getTitle());
                assertNull("a missing date must not throw", items.get(0).getCreated());
                assertNull("a missing date must not throw", items.get(0).getModified());
            }
        }
    }

    private static String entry(final String id, final String title, final String created, final String modified) {
        final StringBuilder xml = new StringBuilder();
        xml.append("<entry><link rel=\"edit\" href=\"Items(")
                .append(id)
                .append(")\"/><content><m:properties ")
                .append("xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">")
                .append("<d:Id>")
                .append(id)
                .append("</d:Id>")
                .append("<d:Title>")
                .append(title)
                .append("</d:Title>")
                .append("<d:Attachments m:type=\"Edm.Boolean\">false</d:Attachments>");
        if (created != null) {
            xml.append("<d:Created>").append(created).append("</d:Created>");
        }
        if (modified != null) {
            xml.append("<d:Modified>").append(modified).append("</d:Modified>");
        }
        xml.append("</m:properties></content></entry>");
        return xml.toString();
    }
}
