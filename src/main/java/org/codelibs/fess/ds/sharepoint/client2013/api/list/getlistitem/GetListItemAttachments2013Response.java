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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachmentsResponse;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class GetListItemAttachments2013Response extends GetListItemAttachmentsResponse {
    private final List<AttachmentFile> files = new ArrayList<>();

    public static GetListItemAttachments2013Response build(final SharePointApi.XmlResponse xmlResponse) {
        final GetListItemAttachments2013Response response = new GetListItemAttachments2013Response();
        final GetListItemAttachmentsDocHandler handler = new GetListItemAttachmentsDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) dataMap.get("value");
        valueList.stream().forEach(value -> {
            final String fileName = DocumentUtil.getValue(value, "FileName", String.class);
            final String serverRelativeUrl = DocumentUtil.getValue(value, "ServerRelativeUrl", String.class);
            response.files.add(new AttachmentFile(fileName, serverRelativeUrl));
        });

        return response;
    }

    @Override
    public List<AttachmentFile> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(100);
        files.stream().forEach(file -> {
            sb.append('[');
            sb.append("file:").append(file.getFileName());
            sb.append(" url:").append(file.getServerRelativeUrl());
            sb.append("] ");
        });
        return sb.toString();
    }

    private static class GetListItemAttachmentsDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
            } else if ("d:FileName".equals(qName)) {
                fieldName = "FileName";
                buffer.setLength(0);
            } else if ("d:ServerRelativeUrl".equals(qName)) {
                fieldName = "ServerRelativeUrl";
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

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
