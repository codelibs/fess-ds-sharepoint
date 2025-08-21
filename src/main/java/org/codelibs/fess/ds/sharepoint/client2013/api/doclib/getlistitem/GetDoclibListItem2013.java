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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getlistitem;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItem;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 specific implementation for retrieving document library list item metadata.
 * This class extends the base GetDoclibListItem functionality to work with SharePoint 2013's
 * XML-based API responses instead of JSON.
 */
public class GetDoclibListItem2013 extends GetDoclibListItem {
    /** The server-relative URL of the SharePoint folder/file (2013 specific). */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetDoclibListItem2013 API client for SharePoint 2013.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetDoclibListItem2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the SharePoint folder/file to retrieve list item data for.
     *
     * @param serverRelativeUrl the server-relative URL path
     * @return this instance for method chaining
     */
    @Override
    public GetDoclibListItem2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the API request to retrieve document library list item metadata from SharePoint 2013.
     * This method uses XML parsing instead of JSON to handle SharePoint 2013's response format.
     *
     * @return the response containing list ID and item ID
     * @throws SharePointClientException if serverRelativeUrl is not set or if the request fails
     */
    @Override
    public GetDoclibListItem2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        final GetDoclibListItemHandler handler = new GetDoclibListItemHandler();
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        xmlResponse.parseXml(handler);
        final Map<String, Object> bodyMap = handler.getDataMap();
        try {
            final String itemId = DocumentUtil.getValue(bodyMap, "Id", String.class);
            final String listId = getListId(DocumentUtil.getValue(bodyMap, "odata.editLink", String.class));
            return new GetDoclibListItem2013Response(listId, itemId);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the SharePoint 2013 REST API URL for retrieving list item fields.
     * Uses the legacy GetFolderByServerRelativeUrl endpoint for SharePoint 2013 compatibility.
     *
     * @return the complete API URL
     */
    @Override
    protected String buildUrl() {
        return siteUrl + "_api/Web/GetFolderByServerRelativeUrl('" + encodeRelativeUrl(serverRelativeUrl) + "')/ListItemAllFields";
    }

    /**
     * Extracts the list ID from the OData edit link (SharePoint 2013 implementation).
     *
     * @param editLink the OData edit link containing the list GUID
     * @return the extracted list ID, or null if editLink is null
     */
    @Override
    protected String getListId(final String editLink) {
        if (editLink == null) {
            return null;
        }
        return editLink.substring(editLink.indexOf("(guid'") + "(guid'".length(), editLink.indexOf("')"));
    }

    /**
     * SAX handler for parsing XML responses from SharePoint 2013 GetDoclibListItem API calls.
     * This handler extracts list item metadata from the XML response format used by SharePoint 2013.
     */
    private static class GetDoclibListItemHandler extends DefaultHandler {
        /** Map to store parsed data from the XML response. */
        private final Map<String, Object> dataMap = new HashMap<>();

        /** Current field name being parsed. */
        private String fieldName;

        /** Buffer for accumulating character data during XML parsing. */
        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Called at the start of XML document parsing.
         * Initializes the data map and sets existence flag.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("Exists", true);
        }

        /**
         * Called when an XML element starts.
         * Handles extraction of item ID and edit link attributes.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         * @param attributes the element attributes
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("link".equals(qName)) {
                final String rel = attributes.getValue("rel");
                if ("edit".equals(rel)) {
                    dataMap.put("odata.editLink", attributes.getValue("href"));
                }
            }
        }

        /**
         * Called when character data is encountered.
         * Accumulates character data for the current field.
         *
         * @param ch the character array
         * @param offset the start offset in the array
         * @param length the number of characters to use
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Called when an XML element ends.
         * Stores the accumulated field data in the data map.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         */
        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            if (fieldName != null) {
                if (!dataMap.containsKey(fieldName)) {
                    dataMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        /**
         * Called at the end of XML document parsing.
         * No specific action required for this handler.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Returns the parsed data map containing extracted field values.
         *
         * @return the data map with parsed field values
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
