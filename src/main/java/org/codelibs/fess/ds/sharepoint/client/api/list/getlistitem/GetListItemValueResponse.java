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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.util.DocumentUtil;

/**
 * Response class for GetListItemValue API containing detailed field values of a SharePoint list item.
 * This class encapsulates all the field values and metadata for a single list item.
 */
public class GetListItemValueResponse implements SharePointApiResponse {
    private static final Logger logger = LogManager.getLogger(GetListItemValueResponse.class);

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

    /**
     * Default constructor for GetListItemValueResponse.
     * Creates an empty response instance with default field values.
     */
    public GetListItemValueResponse() {
        // Default constructor - fields are initialized with default values above
    }

    /**
     * Returns the unique identifier of the list item.
     *
     * @return the item ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the title of the list item.
     *
     * @return the item title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the last modification date of the list item.
     *
     * @return the modification date
     */
    public Date getModified() {
        return modified;
    }

    /**
     * Returns the creation date of the list item.
     *
     * @return the creation date
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Returns the author of the list item.
     *
     * @return the author name
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Returns the last editor of the list item.
     *
     * @return the editor name
     */
    public String getEditor() {
        return editor;
    }

    /**
     * Returns whether the list item has attachments.
     *
     * @return true if the item has attachments, false otherwise
     */
    public boolean isHasAttachments() {
        return hasAttachments;
    }

    /**
     * Returns the order value of the list item.
     *
     * @return the order value
     */
    public long getOrder() {
        return order;
    }

    /**
     * Returns the edit link for the list item.
     *
     * @return the edit link URL
     */
    public String getEditLink() {
        return editLink;
    }

    /**
     * Returns the file reference path of the list item.
     *
     * @return the file reference path
     */
    public String getFileRef() {
        return fileRef;
    }

    /**
     * Returns the file directory reference of the list item.
     *
     * @return the file directory reference
     */
    public String getFileDirRef() {
        return fileDirRef;
    }

    /**
     * Returns the file leaf reference (filename) of the list item.
     *
     * @return the file leaf reference
     */
    public String getFileLeafRef() {
        return fileLeafRef;
    }

    /**
     * Returns the parent item ID of the list item.
     *
     * @return the parent item ID
     */
    public String getParentItemId() {
        return parentItemId;
    }

    /**
     * Returns the parent folder ID of the list item.
     *
     * @return the parent folder ID
     */
    public String getParentFolderId() {
        return parentFolderId;
    }

    /**
     * Returns the file system object type of the list item.
     *
     * @return the file system object type
     */
    public int getFsObjType() {
        return fsObjType;
    }

    /**
     * Returns all field values of the list item as a map.
     *
     * @return the map of field names to values
     */
    public Map<String, String> getValues() {
        return values;
    }

    /**
     * Builds a GetListItemValueResponse from the JSON response received from SharePoint.
     *
     * @param jsonResponse the JSON response from the SharePoint API
     * @return the parsed response containing item field values
     */
    public static GetListItemValueResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT);

        final GetListItemValueResponse response = new GetListItemValueResponse();
        response.id = DocumentUtil.getValue(jsonMap, "ID", String.class);
        response.title = DocumentUtil.getValue(jsonMap, "Title", String.class,
                DocumentUtil.getValue(jsonMap, "FileLeafRef", String.class, StringUtil.EMPTY));
        response.modified = parseDate(sdf, DocumentUtil.getValue(jsonMap, "Modified", String.class));
        response.created = parseDate(sdf, DocumentUtil.getValue(jsonMap, "Created", String.class));
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

    /**
     * Parses a date rendered in the site's regional format, tolerating an absent or unexpected
     * value.
     *
     * <p>{@code FieldValuesAsText} renders every field the way the site is configured to display
     * it, so a site using a format other than the one hard-coded here would otherwise throw while
     * the item is being built and lose the whole item - title, content and permissions included -
     * rather than just its date. Callers that have the raw ISO 8601 {@code Created}/{@code
     * Modified} values from the list item listing should prefer those instead; this exists for
     * the case where only this rendered text is available.
     *
     * @param sdf the date format to parse with
     * @param value the rendered date text, may be null
     * @return the parsed date, or null if {@code value} is null or does not match the format
     */
    private static Date parseDate(final SimpleDateFormat sdf, final String value) {
        if (value == null) {
            return null;
        }
        try {
            return sdf.parse(value);
        } catch (final ParseException e) {
            logger.warn("Failed to parse date: {}", value, e);
            return null;
        }
    }

    /**
     * Returns a string representation of the list item response.
     *
     * @return a formatted string containing item details
     */
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
