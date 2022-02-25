/*
 * Copyright 2012-2022 CodeLibs Project and the Others.
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

public class GetFolder2013Response extends GetFolderResponse {

    public static GetFolder2013Response build(final SharePointApi.XmlResponse xmlResponse) {
        final GetFolderDocHandler handler = new GetFolderDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();
        return build(dataMap);

    }

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

    private static class GetFolderDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("Exists", true);
        }

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

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            if (fieldName != null) {
                if (!dataMap.containsKey(fieldName)) {
                    dataMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
