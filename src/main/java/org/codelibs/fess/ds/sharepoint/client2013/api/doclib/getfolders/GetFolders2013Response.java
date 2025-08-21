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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolder.GetFolder2013Response;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 specific response class for GetFolders API.
 * This class extends GetFoldersResponse to handle XML response parsing
 * specific to SharePoint 2013 REST API format.
 */
public class GetFolders2013Response extends GetFoldersResponse {
    /** List of folder responses specific to SharePoint 2013 */
    private final List<GetFolderResponse> folders = new ArrayList<>();

    /**
     * Default constructor for GetFolders2013Response.
     * Creates an empty response instance that can be populated with folder data.
     */
    public GetFolders2013Response() {
        super();
    }

    /**
     * Gets the list of folders retrieved from SharePoint 2013.
     *
     * @return list of GetFolderResponse objects
     */
    @Override
    public List<GetFolderResponse> getFolders() {
        return folders;
    }

    /**
     * Builds a GetFolders2013Response from an XML response.
     * Parses SharePoint 2013 XML format to extract folder information.
     *
     * @param xmlResponse the XML response from SharePoint 2013 API
     * @return GetFolders2013Response instance containing parsed folder data
     */
    public static GetFolders2013Response build(final SharePointApi.XmlResponse xmlResponse) {
        final GetFoldersDocHandler handler = new GetFoldersDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List<Map<String, Object>>) dataMap.get("value");

        final GetFolders2013Response response = new GetFolders2013Response();
        results.stream().forEach(result -> {
            final GetFolder2013Response folderResponse = GetFolder2013Response.build(result);
            response.folders.add(folderResponse);
        });
        return response;
    }

    /**
     * SAX handler for parsing SharePoint 2013 XML responses containing folder data.
     * This handler extracts folder information from the XML structure.
     */
    private static class GetFoldersDocHandler extends DefaultHandler {
        /** Map to store parsed data */
        private final Map<String, Object> dataMap = new HashMap<>();
        /** Current folder being parsed */
        private Map<String, Object> resultMap = null;

        /** Current field name being parsed */
        private String fieldName;

        /** Buffer for collecting character data */
        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Handles the start of the XML document.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        /**
         * Handles the start of an XML element.
         *
         * @param uri namespace URI
         * @param localName local name
         * @param qName qualified name
         * @param attributes element attributes
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
                resultMap.put("Exists", true);
            } else if ("id".equals(qName)) {
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
         * Handles character data within XML elements.
         *
         * @param ch character array
         * @param offset starting offset
         * @param length number of characters
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Handles the end of an XML element.
         *
         * @param uri namespace URI
         * @param localName local name
         * @param qName qualified name
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
         * Handles the end of the XML document.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Gets the parsed data map containing folder information.
         *
         * @return map containing parsed folder data
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
