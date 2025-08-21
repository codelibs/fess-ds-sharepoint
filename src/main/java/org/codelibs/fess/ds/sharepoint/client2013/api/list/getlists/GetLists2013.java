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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetLists;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 implementation for retrieving SharePoint lists.
 * This class extends GetLists to provide XML parsing capabilities
 * specific to SharePoint 2013's REST API response format.
 *
 * <p>Uses SAX XML parsing to extract list metadata from SharePoint 2013
 * responses and creates SharePointList objects with the retrieved information.</p>
 *
 * @see GetLists
 * @see GetLists2013Response
 */
public class GetLists2013 extends GetLists {
    /** SharePoint 2013 API endpoint path for lists */
    private static final String API_PATH = "_api/lists";

    /**
     * Constructs a new GetLists2013 instance.
     *
     * @param client HTTP client for making API requests
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication handler
     */
    public GetLists2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Executes the SharePoint 2013 lists retrieval request.
     *
     * @return GetLists2013Response containing parsed list information
     */
    @Override
    public GetLists2013Response execute() {
        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH);
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        return buildResponse(xmlResponse);
    }

    /**
     * Builds the response object from parsed XML data.
     * Processes the XML response to extract list information and
     * creates SharePointList objects for each found list.
     *
     * @param xmlResponse the XML response from SharePoint 2013
     * @return GetLists2013Response with parsed list data
     */
    private GetLists2013Response buildResponse(final XmlResponse xmlResponse) {
        final GetListsDocHandler handler = new GetListsDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        final List<GetLists2013Response.SharePointList> sharePointLists = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) dataMap.get("value");
        valueList.forEach(value -> {
            final String titleObj = DocumentUtil.getValue(value, "Title", String.class);
            if (titleObj == null) {
                return;
            }
            final String idObj = DocumentUtil.getValue(value, "Id", String.class);
            if (idObj == null) {
                return;
            }
            final String entityTypeName = DocumentUtil.getValue(dataMap, "EntityTypeName", String.class);
            if (entityTypeName == null) {
                return;
            }
            final boolean noCrawl = DocumentUtil.getValue(value, "NoCrawl", Boolean.class, Boolean.TRUE);
            final boolean hidden = DocumentUtil.getValue(dataMap, "Hidden", Boolean.class, Boolean.FALSE);
            final GetLists2013Response.SharePointList sharePointList =
                    new GetLists2013Response.SharePointList(idObj, titleObj, noCrawl, hidden, entityTypeName);
            sharePointLists.add(sharePointList);
        });

        return new GetLists2013Response(sharePointLists);
    }

    /**
     * SAX handler for parsing SharePoint 2013 XML responses containing lists data.
     * This handler extracts list properties from the XML structure.
     */
    private static class GetListsDocHandler extends DefaultHandler {
        /** Map to store all parsed list data */
        private final Map<String, Object> dataMap = new HashMap<>();
        /** Current list being parsed */
        private Map<String, Object> resultMap = null;

        /** Current field name being processed */
        private String fieldName;

        /** Buffer for accumulating character data */
        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Called at the start of document parsing to initialize data structures.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        /**
         * Called when an XML element starts, identifies list properties to extract.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         * @param attributes the element attributes
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
                resultMap.put("Exists", true);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:NoCrawl".equals(qName)) {
                fieldName = "NoCrawl";
                buffer.setLength(0);
            } else if ("d:Hidden".equals(qName)) {
                fieldName = "Hidden";
                buffer.setLength(0);
            } else if ("d:EntityTypeName".equals(qName)) {
                fieldName = "EntityTypeName";
                buffer.setLength(0);
            }
        }

        /**
         * Accumulates character data for the current field being processed.
         *
         * @param ch the character array
         * @param offset the start offset
         * @param length the number of characters to use
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Called when an XML element ends, stores the accumulated field value.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         */
        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if ("entry".equals(qName)) {
                if (resultMap != null) {
                    ((List) dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else if (resultMap != null && fieldName != null) {
                if (!resultMap.containsKey(fieldName)) {
                    resultMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        /**
         * Called when document parsing is complete.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Gets the map containing all parsed list data.
         *
         * @return the data map with list information
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
