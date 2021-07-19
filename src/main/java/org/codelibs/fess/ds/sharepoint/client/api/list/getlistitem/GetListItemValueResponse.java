/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

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
        response.id = jsonMap.get("ID").toString();
        response.title = jsonMap.getOrDefault("Title", jsonMap.getOrDefault("FileLeafRef", "")).toString();
        response.modified = sdf.parse(jsonMap.get("Modified").toString());
        response.created = sdf.parse(jsonMap.get("Created").toString());
        response.author = jsonMap.get("Author").toString();
        response.editor = jsonMap.get("Editor").toString();
        response.fileRef = jsonMap.getOrDefault("FileRef", "").toString();
        response.fileDirRef = jsonMap.getOrDefault("FileDirRef", "").toString();
        response.fileLeafRef = jsonMap.getOrDefault("FileLeafRef", "").toString();
        response.parentItemId = jsonMap.getOrDefault("ParentItemID", "").toString();
        response.parentFolderId = jsonMap.getOrDefault("ParentFolderID", "").toString();
        response.fsObjType = Integer.parseInt(jsonMap.getOrDefault("FSObjType", "0").toString());
        if (jsonMap.containsKey("Attachments")) {
            response.hasAttachments = Boolean.parseBoolean(jsonMap.get("Attachments").toString());
        }
        if (jsonMap.containsKey("Order") && StringUtils.isNotBlank(jsonMap.get("Order").toString())) {
            response.order = Long.parseLong(jsonMap.get("Order").toString().replace(",", ""));
        } else {
            response.order = -1;
        }
        response.editLink = jsonMap.get("odata.editLink").toString();

        jsonMap.entrySet().stream().forEach(entry -> response.values.put(entry.getKey(), entry.getValue().toString()));

        return response;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
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
