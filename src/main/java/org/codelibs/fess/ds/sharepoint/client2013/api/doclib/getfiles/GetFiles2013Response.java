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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles.GetFilesResponse;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 specific response class for document library file retrieval operations.
 * Extends GetFilesResponse with XML parsing capabilities for SharePoint 2013 API responses.
 */
public class GetFiles2013Response extends GetFilesResponse {

    /**
     * Default constructor for GetFiles2013Response.
     * Creates an empty response instance that can be populated with file data.
     */
    public GetFiles2013Response() {
        super();
    }

    /**
     * Gets the list of files retrieved from the SharePoint 2013 document library.
     *
     * @return list of document library files
     */
    @Override
    public List<DocLibFile> getFiles() {
        return files;
    }

    /**
     * Builds a GetFiles2013Response from an XML response received from SharePoint 2013 API.
     *
     * @param xmlResponse the XML response from SharePoint 2013 API
     * @return constructed GetFiles2013Response containing parsed file information
     */
    public static GetFiles2013Response build(final SharePointApi.XmlResponse xmlResponse) {
        final GetFilesDocHandler handler = new GetFilesDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List) dataMap.get("value");

        final GetFiles2013Response response = new GetFiles2013Response();
        results.stream().forEach(result -> {
            final DocLibFile docLibFile = createDocLibFile(result);
            response.files.add(docLibFile);
        });

        return response;
    }

    /**
     * SAX handler for parsing SharePoint 2013 XML responses containing file information.
     */
    private static class GetFilesDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

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
         * Called when an XML element starts, processes file-related elements.
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
            } else if ("id".equals(qName)) {
                fieldName = "UniqueId";
                buffer.setLength(0);
            } else if ("d:Name".equals(qName)) {
                fieldName = "Name";
                buffer.setLength(0);
            } else if ("d:ServerRelativeUrl".equals(qName)) {
                fieldName = "ServerRelativeUrl";
                buffer.setLength(0);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:TimeCreated".equals(qName)) {
                fieldName = "TimeCreated";
                buffer.setLength(0);
            } else if ("d:TimeLastModified".equals(qName)) {
                fieldName = "TimeLastModified";
                buffer.setLength(0);
            }
        }

        /**
         * Called to process character data within XML elements.
         *
         * @param ch the character array
         * @param offset the start offset in the array
         * @param length the number of characters to read
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Called when an XML element ends, finalizes file data processing.
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
         * Called at the end of document parsing.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Gets the parsed data map containing file information.
         *
         * @return map containing parsed file data
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
