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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.util.DocumentUtil;

/**
 * Response class for SharePoint document library file retrieval operations.
 * Contains a list of files retrieved from a SharePoint document library.
 */
public class GetFilesResponse implements SharePointApiResponse {
    private static final Logger logger = LogManager.getLogger(GetFilesResponse.class);

    /** List of files retrieved from the SharePoint document library */
    protected final List<DocLibFile> files = new ArrayList<>();

    /**
     * Default constructor for GetFilesResponse.
     * Creates an empty response instance that can be populated with file data.
     */
    public GetFilesResponse() {
        // Default constructor - no initialization needed beyond field declarations
    }

    /**
     * Gets the list of files retrieved from the SharePoint document library.
     *
     * @return list of document library files
     */
    public List<DocLibFile> getFiles() {
        return files;
    }

    /**
     * Builds a GetFilesResponse from a JSON response received from SharePoint API.
     *
     * @param jsonResponse the JSON response from SharePoint API
     * @return constructed GetFilesResponse containing parsed file information
     */
    public static GetFilesResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List<Map<String, Object>>) jsonMap.get("value");

        final GetFilesResponse response = new GetFilesResponse();
        results.stream().forEach(result -> {
            final DocLibFile docLibFile = createDocLibFile(result);
            response.files.add(docLibFile);
        });

        return response;
    }

    /**
     * Creates a DocLibFile instance from SharePoint API data map.
     *
     * @param dataMap map containing file data from SharePoint API
     * @return DocLibFile instance with populated file information
     */
    protected static DocLibFile createDocLibFile(final Map<String, Object> dataMap) {
        final DocLibFile docLibFile = new DocLibFile();
        docLibFile.fileName = DocumentUtil.getValue(dataMap, "Name", String.class);
        docLibFile.title = DocumentUtil.getValue(dataMap, "Title", String.class, StringUtil.EMPTY);
        docLibFile.serverRelativeUrl = DocumentUtil.getValue(dataMap, "ServerRelativeUrl", String.class);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            final String created = DocumentUtil.getValue(dataMap, "TimeCreated", String.class);
            if (created != null) {
                docLibFile.created = sdf.parse(created);
            }
            final String modified = DocumentUtil.getValue(dataMap, "TimeLastModified", String.class);
            if (modified != null) {
                docLibFile.modified = sdf.parse(modified);
            }
        } catch (final ParseException e) {
            logger.warn("Failed to parse date.", e);
        }
        return docLibFile;
    }

    /**
     * Represents a file in a SharePoint document library with metadata.
     */
    public static class DocLibFile {
        private String fileName;
        private String title;
        private String serverRelativeUrl;
        private Date created;
        private Date modified;

        /**
         * Default constructor for DocLibFile.
         * Creates an empty file instance that can be populated with file metadata.
         */
        public DocLibFile() {
            // Default constructor - no initialization needed beyond field declarations
        }

        /**
         * Gets the file name.
         *
         * @return the file name
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * Gets the file title.
         *
         * @return the file title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Gets the server relative URL of the file.
         *
         * @return the server relative URL
         */
        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }

        /**
         * Gets the file creation date.
         *
         * @return the creation date
         */
        public Date getCreated() {
            return created;
        }

        /**
         * Gets the file last modification date.
         *
         * @return the last modification date
         */
        public Date getModified() {
            return modified;
        }

    }
}
