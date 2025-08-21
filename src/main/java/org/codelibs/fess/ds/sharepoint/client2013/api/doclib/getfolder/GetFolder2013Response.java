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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013-specific response object for folder information retrieved from document libraries.
 * This class extends the base GetFolderResponse and provides XML parsing capabilities
 * specific to SharePoint 2013's response format.
 *
 * <p>Unlike newer SharePoint versions that return JSON, SharePoint 2013 returns XML responses
 * that require specialized parsing using SAX handlers.</p>
 *
 * @see GetFolderResponse
 * @see GetFolder2013
 */
public class GetFolder2013Response extends GetFolderResponse {

    /**
     * Default constructor for GetFolder2013Response.
     * Creates an empty response instance that can be populated with folder data.
     */
    public GetFolder2013Response() {
        super();
    }

    /**
     * Builds a GetFolder2013Response from an XML response.
     * This method parses the XML using a custom SAX handler to extract folder data.
     *
     * @param xmlResponse the XML response from SharePoint 2013 API
     * @return a new GetFolder2013Response instance populated with folder data
     */
    public static GetFolder2013Response build(final SharePointApi.XmlResponse xmlResponse) {
        final GetFolderDocHandler handler = new GetFolderDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();
        return build(dataMap);

    }

    /**
     * Builds a GetFolder2013Response from a map containing folder data.
     * This method extracts folder properties specific to SharePoint 2013 format
     * and creates a response object with parsed timestamps and metadata.
     *
     * @param dataMap the map containing folder data parsed from SharePoint 2013 XML
     * @return a new GetFolder2013Response instance populated with the data
     * @throws SharePointClientException if date parsing fails
     */
    public static GetFolder2013Response build(final Map<String, Object> dataMap) {

        final GetFolder2013Response response = new GetFolder2013Response();
        response.id = DocumentUtil.getValue(dataMap, "UniqueId", String.class);
        response.name = DocumentUtil.getValue(dataMap, "Name", String.class);
        response.exists = DocumentUtil.getValue(dataMap, "Exists", Boolean.class, false);
        response.serverRelativeUrl = DocumentUtil.getValue(dataMap, "ServerRelativeUrl", String.class);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            if (dataMap.containsKey("TimeCreated")) {
                response.created = sdf.parse(DocumentUtil.getValue(dataMap, "TimeCreated", String.class));
            }
            response.modified = sdf.parse(DocumentUtil.getValue(dataMap, "TimeLastModified", String.class));
        } catch (final ParseException e) {
            throw new SharePointClientException(e);
        }
        response.itemCount = DocumentUtil.getValue(dataMap, "ItemCount", Integer.class, 0);
        return response;
    }

    /**
     * SAX handler for parsing SharePoint 2013 XML responses containing folder information.
     * This handler extracts folder properties from the XML structure and builds a data map.
     */
    private static class GetFolderDocHandler extends DefaultHandler {
        /** Map to store extracted folder data */
        private final Map<String, Object> dataMap = new HashMap<>();

        /** Current field name being processed */
        private String fieldName;

        /** Buffer for accumulating character data */
        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Initializes the document parsing by clearing the data map and setting default values.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("Exists", true);
        }

        /**
         * Processes the start of an XML element and identifies folder properties to extract.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         * @param attributes the element attributes
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("id".equals(qName)) {
                fieldName = "UniqueId";
                buffer.setLength(0);
            } else if ("d:Name".equals(qName)) {
                fieldName = "Name";
                buffer.setLength(0);
            } else if ("d:ServerRelativeUrl".equals(qName)) {
                fieldName = "ServerRelativeUrl";
                buffer.setLength(0);
            } else if ("d:ItemCount".equals(qName)) {
                fieldName = "ItemCount";
                buffer.setLength(0);
            } else if ("updated".equals(qName)) {
                fieldName = "TimeLastModified";
                buffer.setLength(0);
            }
        }

        /**
         * Accumulates character data for the current field being processed.
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
         * Processes the end of an XML element and stores the accumulated field value.
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
         * Called when the document parsing is complete. No additional processing needed.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Gets the map containing all extracted folder data.
         *
         * @return the data map with folder properties
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
