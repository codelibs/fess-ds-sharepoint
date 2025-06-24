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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class GetListItemValue2013Response extends GetListItemValueResponse {

    private String id;
    private String title;
    private Date modified;
    private Date created;
    private String author;
    private String editor;
    private boolean hasAttachments = false;
    private long order = 0;
    private String editLink;
    private String fileRef;
    private String fileDirRef;
    private String fileLeafRef;
    private String parentItemId;
    private String parentFolderId;
    private int fsObjType;
    private final Map<String, String> values = new HashMap<>();

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Date getModified() {
        return modified;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getEditor() {
        return editor;
    }

    @Override
    public boolean isHasAttachments() {
        return hasAttachments;
    }

    @Override
    public long getOrder() {
        return order;
    }

    @Override
    public String getEditLink() {
        return editLink;
    }

    @Override
    public String getFileRef() {
        return fileRef;
    }

    @Override
    public String getFileDirRef() {
        return fileDirRef;
    }

    @Override
    public String getFileLeafRef() {
        return fileLeafRef;
    }

    @Override
    public String getParentItemId() {
        return parentItemId;
    }

    @Override
    public String getParentFolderId() {
        return parentFolderId;
    }

    @Override
    public int getFsObjType() {
        return fsObjType;
    }

    @Override
    public Map<String, String> getValues() {
        return values;
    }

    public static GetListItemValue2013Response build(final SharePointApi.XmlResponse xmlResponse) throws ParseException {
        final GetListItemValueDocHandler handler = new GetListItemValueDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");

        final GetListItemValue2013Response response = new GetListItemValue2013Response();
        response.id = DocumentUtil.getValue(dataMap, "ID", String.class);
        response.title = DocumentUtil.getValue(dataMap, "Title", String.class, StringUtil.EMPTY);
        response.modified = sdf.parse(DocumentUtil.getValue(dataMap, "Modified", String.class));
        response.created = sdf.parse(DocumentUtil.getValue(dataMap, "Created", String.class));
        response.author = DocumentUtil.getValue(dataMap, "Author", String.class, StringUtil.EMPTY);
        response.editor = DocumentUtil.getValue(dataMap, "Editor", String.class, StringUtil.EMPTY);
        response.fileRef = DocumentUtil.getValue(dataMap, "FileRef", String.class, StringUtil.EMPTY);
        response.fileDirRef = DocumentUtil.getValue(dataMap, "FileDirRef", String.class, StringUtil.EMPTY);
        response.fileLeafRef = DocumentUtil.getValue(dataMap, "FileLeafRef", String.class, StringUtil.EMPTY);
        response.parentItemId = DocumentUtil.getValue(dataMap, "ParentItemID", String.class, StringUtil.EMPTY);
        response.parentFolderId = DocumentUtil.getValue(dataMap, "ParentFolderID", String.class, StringUtil.EMPTY);
        response.fsObjType = DocumentUtil.getValue(dataMap, "FSObjType", Integer.class, 0);
        if (dataMap.containsKey("Attachments")) {
            response.hasAttachments = DocumentUtil.getValue(dataMap, "Attachments", Boolean.class, false);
        }
        response.order = Long.parseLong(DocumentUtil.getValue(dataMap, "Order", String.class).replace(",", StringUtil.EMPTY));
        response.editLink = DocumentUtil.getValue(dataMap, "odata.editLink", String.class);

        dataMap.entrySet().stream().forEach(entry -> response.values.put(entry.getKey(), entry.getValue().toString()));

        return response;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(100);
        sb.append(String.format("[id:%s] [title:%s] [modified:%tF] [created:%tF] [author:%s] [editor:%s] [hasAttachments:%s] [order:%d]",
                id, title, modified, created, author, editor, hasAttachments, order));
        values.entrySet().stream().forEach(entry -> {
            sb.append(" [");
            sb.append("value-").append(entry.getKey()).append(':').append(entry.getValue());
            sb.append("]");
        });

        return sb.toString();
    }

    private static class GetListItemValueDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        private boolean nowCountent = false;

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("Exists", true);
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            final String nonePredfixQName = qName.substring(2);
            if ("content".equals(qName)) {
                nowCountent = true;
            } else if ("d:ID".equals(qName)) {
                fieldName = "ID";
                buffer.setLength(0);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:Modified".equals(qName)) {
                fieldName = "Modified";
                buffer.setLength(0);
            } else if ("d:Created".equals(qName)) {
                fieldName = "Created";
                buffer.setLength(0);
            } else if ("d:Author".equals(qName)) {
                fieldName = "Author";
                buffer.setLength(0);
            } else if ("d:Editor".equals(qName)) {
                fieldName = "Editor";
                buffer.setLength(0);
            } else if ("d:Attachments".equals(qName)) {
                fieldName = "Attachments";
                buffer.setLength(0);
            } else if ("d:Order".equals(qName)) {
                fieldName = "Order";
                buffer.setLength(0);
            } else if ("link".equals(qName)) {
                final String rel = attributes.getValue("rel");
                if ("edit".equals(rel)) {
                    dataMap.put("odata.editLink", attributes.getValue("href"));
                }
            } else if ("d:FileRef".equals(qName)) {
                fieldName = "FileRef";
                buffer.setLength(0);
            } else if ("d:FileDirRef".equals(qName)) {
                fieldName = "FileDirRef";
                buffer.setLength(0);
            } else if ("d:FileLeafRef".equals(qName)) {
                fieldName = "FileLeafRef";
                buffer.setLength(0);
            } else if ("d:ParentItemID".equals(qName)) {
                fieldName = "ParentItemID";
                buffer.setLength(0);
            } else if ("d:ParentFolderID".equals(qName)) {
                fieldName = "ParentFolderID";
                buffer.setLength(0);
            } else if ("d:FSObjType".equals(qName)) {
                fieldName = "FSObjType";
                buffer.setLength(0);
            } else if (nowCountent) {
                fieldName = nonePredfixQName;
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
            if ("content".equals(qName)) {
                nowCountent = false;
            } else if (fieldName != null) {
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
