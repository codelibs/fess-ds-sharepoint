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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GetListItemValueResponse implements SharePointApiResponse {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    private String id;
    private String title;
    private Date modified;
    private Date created;
    private String author;
    private String editor;
    private boolean hasAttachments = false;
    private long order = 0;
    private String editLink;
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

    public Map<String, String> getValues() {
        return values;
    }

    public static GetListItemValueResponse build(final SharePointApi.JsonResponse jsonResponse) throws ParseException {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        GetListItemValueResponse response = new GetListItemValueResponse();
        response.id = jsonMap.get("ID").toString();
        response.title = jsonMap.get("Title").toString();
        response.modified = sdf.parse(jsonMap.get("Modified").toString());
        response.created = sdf.parse(jsonMap.get("Created").toString());
        response.author = jsonMap.get("Author").toString();
        response.editor = jsonMap.get("Editor").toString();
        if (jsonMap.containsKey("Attachments")) {
            response.hasAttachments = Boolean.valueOf(jsonMap.get("Attachments").toString());
        }
        response.order = Long.valueOf(jsonMap.get("Order").toString().replace(",", ""));
        response.editLink = jsonMap.get("odata.editLink").toString();

        jsonMap.entrySet().stream()
                .filter(entry -> !isExcludeField(entry.getKey()))
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

    private static final String[] EXCLUDE_FIELDS = {
            "odata.metadata",
            "odata.type",
            "odata.id",
            "odata.editLink",
            "ContentTypeId",
            "Title",
            "File_x005f_x0020_x005f_Type",
            "ComplianceAssetId",
            "ID",
            "Modified",
            "Created",
            "Author",
            "Editor",
            "OData__x005f_HasCopyDestinations",
            "OData__x005f_CopySource",
            "owshiddenversion",
            "WorkflowVersion",
            "OData__x005f_UIVersion",
            "OData__x005f_UIVersionString",
            "Attachments",
            "OData__x005f_ModerationStatus",
            "InstanceID",
            "Order",
            "GUID",
            "WorkflowInstanceID",
            "FileRef",
            "FileDirRef",
            "Last_x005f_x0020_x005f_Modified",
            "Created_x005f_x0020_x005f_Date",
            "FSObjType",
            "SortBehavior",
            "FileLeafRef",
            "UniqueId",
            "SyncClientId",
            "ProgId",
            "ScopeId",
            "MetaInfo",
            "OData__x005f_Level",
            "OData__x005f_IsCurrentVersion",
            "ItemChildCount",
            "FolderChildCount",
            "Restricted",
            "OriginatorId",
            "NoExecute",
            "ContentVersion",
            "OData__x005f_ComplianceFlags",
            "OData__x005f_ComplianceTag",
            "OData__x005f_ComplianceTagWrittenTime",
            "OData__x005f_ComplianceTagUserId",
            "AccessPolicy",
            "OData__x005f_VirusStatus",
            "OData__x005f_VirusVendorID",
            "OData__x005f_VirusInfo",
            "AppAuthor",
            "AppEditor",
            "SMTotalSize",
            "SMLastModifiedDate",
            "SMTotalFileStreamSize",
            "SMTotalFileCount",
            "OData__x005f_ModerationComments",
    };
    private static boolean isExcludeField(String fieldName) {
        return Arrays.stream(EXCLUDE_FIELDS).anyMatch(excludeFIeld -> excludeFIeld.equals(fieldName));
    }
}
