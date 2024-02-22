/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.util.DocumentUtil;

public class GetListItemValueResponse implements SharePointApiResponse {

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

    public static GetListItemValueResponse build(final SharePointApi.JsonResponse jsonResponse) throws ParseException {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");

        final GetListItemValueResponse response = new GetListItemValueResponse();
        response.id = DocumentUtil.getValue(jsonMap, "ID", String.class);
        response.title = DocumentUtil.getValue(jsonMap, "Title", String.class,
                DocumentUtil.getValue(jsonMap, "FileLeafRef", String.class, StringUtil.EMPTY));
        response.modified = sdf.parse(DocumentUtil.getValue(jsonMap, "Modified", String.class));
        response.created = sdf.parse(DocumentUtil.getValue(jsonMap, "Created", String.class));
        response.author = DocumentUtil.getValue(jsonMap, "Author", String.class);
        response.editor = DocumentUtil.getValue(jsonMap, "Editor", String.class);
        response.fileRef = DocumentUtil.getValue(jsonMap, "FileRef", String.class, StringUtil.EMPTY);
        response.fileDirRef = DocumentUtil.getValue(jsonMap, "FileDirRef", String.class, StringUtil.EMPTY);
        response.fileLeafRef = DocumentUtil.getValue(jsonMap, "FileLeafRef", String.class, StringUtil.EMPTY);
        response.parentItemId = DocumentUtil.getValue(jsonMap, "ParentItemID", String.class, StringUtil.EMPTY);
        response.parentFolderId = DocumentUtil.getValue(jsonMap, "ParentFolderID", String.class, StringUtil.EMPTY);
        response.fsObjType = DocumentUtil.getValue(jsonMap, "FSObjType", Integer.class, 0);
        if (jsonMap.containsKey("Attachments")) {
            response.hasAttachments = DocumentUtil.getValue(jsonMap, "Attachments", Boolean.class, false);
        }
        final String order = DocumentUtil.getValue(jsonMap, "Order", String.class);
        if (StringUtils.isNotBlank(order)) {
            response.order = Long.parseLong(order.replace(",", StringUtil.EMPTY));
        } else {
            response.order = -1;
        }
        response.editLink = DocumentUtil.getValue(jsonMap, "odata.editLink", String.class);

        jsonMap.entrySet().stream().forEach(entry -> response.values.put(entry.getKey(), entry.getValue().toString()));

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
}
