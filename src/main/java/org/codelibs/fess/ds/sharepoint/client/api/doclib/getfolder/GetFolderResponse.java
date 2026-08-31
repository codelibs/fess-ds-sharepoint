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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.util.DocumentUtil;

/**
 * Response object containing folder information retrieved from SharePoint document libraries.
 * This class encapsulates all folder metadata including unique identifier, name, existence status,
 * server-relative URL, creation/modification timestamps, and item count.
 *
 * <p>The response is built from JSON data returned by SharePoint REST API endpoints.</p>
 *
 * @see GetFolder
 * @see SharePointApiResponse
 */
public class GetFolderResponse implements SharePointApiResponse {
    private static final Logger logger = LogManager.getLogger(GetFolderResponse.class);

    /** The unique identifier of the folder */
    protected String id = null;
    /** The display name of the folder */
    protected String name = null;
    /** Whether the folder exists in SharePoint */
    protected boolean exists = false;
    /** The server-relative URL path of the folder */
    protected String serverRelativeUrl = null;
    /** The creation timestamp of the folder */
    protected Date created = null;
    /** The last modification timestamp of the folder */
    protected Date modified = null;
    /** The number of items contained in the folder */
    protected int itemCount = 0;

    /**
     * Default constructor for GetFolderResponse.
     * Creates an empty response instance with default field values.
     */
    public GetFolderResponse() {
        // Default constructor - fields are initialized with default values above
    }

    /**
     * Gets the unique identifier of the folder.
     *
     * @return the folder's unique ID, or null if not available
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name of the folder.
     *
     * @return the folder name, or null if not available
     */
    public String getName() {
        return name;
    }

    /**
     * Checks whether the folder exists in SharePoint.
     *
     * @return true if the folder exists, false otherwise
     */
    public boolean isExists() {
        return exists;
    }

    /**
     * Gets the server-relative URL of the folder.
     *
     * @return the server-relative path to the folder, or null if not available
     */
    public String getServerRelativeUrl() {
        return serverRelativeUrl;
    }

    /**
     * Gets the creation timestamp of the folder.
     *
     * @return the date when the folder was created, or null if not available
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Gets the last modification timestamp of the folder.
     *
     * @return the date when the folder was last modified, or null if not available
     */
    public Date getModified() {
        return modified;
    }

    /**
     * Gets the number of items contained in the folder.
     *
     * @return the count of items in the folder
     */
    public int getItemCount() {
        return itemCount;
    }

    /**
     * Builds a GetFolderResponse from a JSON response.
     *
     * @param jsonResponse the JSON response from SharePoint API
     * @return a new GetFolderResponse instance populated with folder data
     */
    public static GetFolderResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        return buildFromMap(jsonMap);
    }

    /**
     * Builds a GetFolderResponse from a map containing folder data.
     * This method extracts folder properties from the provided map and creates
     * a response object with parsed timestamps and metadata.
     *
     * @param jsonMap the map containing folder data from SharePoint
     * @return a new GetFolderResponse instance populated with the data
     */
    public static GetFolderResponse buildFromMap(final Map<String, Object> jsonMap) {
        final GetFolderResponse response = new GetFolderResponse();
        response.id = DocumentUtil.getValue(jsonMap, "UniqueId", String.class);
        response.name = DocumentUtil.getValue(jsonMap, "Name", String.class);
        response.exists = DocumentUtil.getValue(jsonMap, "Exists", Boolean.class, false);
        response.serverRelativeUrl = DocumentUtil.getValue(jsonMap, "ServerRelativeUrl", String.class);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        response.created = parseDate(sdf, DocumentUtil.getValue(jsonMap, "TimeCreated", String.class));
        response.modified = parseDate(sdf, DocumentUtil.getValue(jsonMap, "TimeLastModified", String.class));
        response.itemCount = DocumentUtil.getValue(jsonMap, "ItemCount", Integer.class, 0);
        return response;
    }

    /**
     * Parses an ISO 8601 date, tolerating an absent or unparseable value.
     *
     * <p>A folder's date failing to parse used to abort the whole subtree at the caller's first
     * call - see {@code FolderCrawl}, which reads this folder before it lists anything inside it.
     * Losing just the date keeps the folder and everything under it.
     *
     * <p>Parsed per field rather than under one {@code try}, so a malformed {@code TimeCreated}
     * costs only itself and leaves {@code TimeLastModified} - the value the index actually uses -
     * intact.
     *
     * @param sdf the date format to parse with
     * @param value the date text, may be null
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
}
