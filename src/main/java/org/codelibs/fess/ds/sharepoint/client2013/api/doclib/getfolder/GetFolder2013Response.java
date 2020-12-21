/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
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

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import java.text.ParseException;
import java.util.*;

public class GetFolder2013Response extends GetFolderResponse {

    public static GetFolder2013Response build(SharePointApi.XmlResponse xmlResponse) {
        final GetFolderDocHandler handler = new GetFolderDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();
        return build(dataMap);

    }

    public static GetFolder2013Response build(final Map<String, Object> dataMap) {

        @SuppressWarnings("unchecked")
        final GetFolder2013Response response = new GetFolder2013Response();
        response.id = dataMap.get("UniqueId").toString();
        response.name = dataMap.get("Name").toString();
        response.exists = Boolean.valueOf(dataMap.get("Exists").toString());
        response.serverRelativeUrl = dataMap.get("ServerRelativeUrl").toString();
        try {
            if (dataMap.containsKey("TimeCreated")) {
                response.created = sdf.parse(dataMap.get("TimeCreated").toString());
            }
            response.modified = sdf.parse(dataMap.get("TimeLastModified").toString());
        } catch (ParseException e) {
            throw new SharePointClientException(e);
        }
        response.itemCount = Integer.valueOf(dataMap.get("ItemCount").toString());
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
