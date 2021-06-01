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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class GetFilesResponse implements SharePointApiResponse {
    private static final Logger logger = LoggerFactory.getLogger(GetFilesResponse.class);

    protected static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    protected final List<DocLibFile> files = new ArrayList<>();

    public List<DocLibFile> getFiles() {
        return files;
    }

    public static GetFilesResponse build(SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List)jsonMap.get("value");

        final GetFilesResponse response = new GetFilesResponse();
        results.stream().forEach(result -> {
            final DocLibFile docLibFile = createDocLibFile(result);
            response.files.add(docLibFile);
        });

        return response;
    }

    protected static DocLibFile createDocLibFile(Map<String, Object> dataMap) {
        final DocLibFile docLibFile = new DocLibFile();
        docLibFile.fileName = dataMap.get("Name").toString();
        docLibFile.title = (String)dataMap.getOrDefault("Title", "");
        docLibFile.serverRelativeUrl = dataMap.get("ServerRelativeUrl").toString();
        try {
            docLibFile.created = sdf.parse(dataMap.get("TimeCreated").toString());
            docLibFile.modified = sdf.parse(dataMap.get("TimeLastModified").toString());
        } catch (ParseException e) {
            logger.warn("Failed to parse date.", e);
        }
        return docLibFile;
    }

    public static class DocLibFile {
        private String fileName;
        private String title;
        private String serverRelativeUrl;
        private Date created;
        private Date modified;

        public String getFileName() {
            return fileName;
        }

        public String getTitle() {
            return title;
        }

        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }

        public Date getCreated() {
            return created;
        }

        public Date getModified() {
            return modified;
        }


    }
}
