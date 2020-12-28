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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GetListItemValue2013Response extends GetListItemValueResponse {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");

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
    private Map<String, String> values = new HashMap<>();

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Date getModified() {
        return modified;
    }

    public Date getCreated() {
        return created;
    }

    public String getAuthor() {
        return author;
    }

    public String getEditor() {
        return editor;
    }

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public long getOrder() {
        return order;
    }

    public String getEditLink() {
        return editLink;
    }

    public String getFileRef() {
        return fileRef;
    }

    public String getFileDirRef() {
        return fileDirRef;
    }

    public String getFileLeafRef() {
        return fileLeafRef;
    }

    public String getParentItemId() {
        return parentItemId;
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public int getFsObjType() {
        return fsObjType;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public static GetListItemValue2013Response build(final SharePointApi.XmlResponse xmlResponse) throws ParseException {
        final GetListItemValueDocHandler handler = new GetListItemValueDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        GetListItemValue2013Response response = new GetListItemValue2013Response();
        response.id = dataMap.get("ID").toString();
        response.title = dataMap.getOrDefault("Title", "").toString();
        response.modified = sdf.parse(dataMap.get("Modified").toString());
        response.created = sdf.parse(dataMap.get("Created").toString());
        response.author = dataMap.get("Author").toString();
        response.editor = dataMap.get("Editor").toString();
        response.fileRef = dataMap.getOrDefault("FileRef", "").toString();
        response.fileDirRef = dataMap.getOrDefault("FileDirRef", "").toString();
        response.fileLeafRef = dataMap.getOrDefault("FileLeafRef", "").toString();
        response.parentItemId = dataMap.getOrDefault("ParentItemID", "").toString();
        response.parentFolderId = dataMap.getOrDefault("ParentFolderID", "").toString();
        response.fsObjType = Integer.valueOf(dataMap.getOrDefault("FSObjType", "0").toString());
        if (dataMap.containsKey("Attachments")) {
            response.hasAttachments = Boolean.valueOf(dataMap.get("Attachments").toString());
        }
        response.order = Long.valueOf(dataMap.get("Order").toString().replace(",", ""));
        response.editLink = dataMap.get("odata.editLink").toString();

        dataMap.entrySet().stream()
                .forEach(entry -> response.values.put(entry.getKey(), entry.getValue().toString()));

        return response;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
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
                String rel = attributes.getValue("rel");
                if (rel != null && rel.equals("edit")) {
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
            }  else if (nowCountent) {
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
