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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;

/**
 * Response class for GetFolders API that contains a list of folders
 * retrieved from SharePoint document libraries.
 */
public class GetFoldersResponse implements SharePointApiResponse {
    /** List of folder responses */
    private final List<GetFolderResponse> folders = new ArrayList<>();

    /**
     * Default constructor for GetFoldersResponse.
     * Creates an empty response instance that can be populated with folder data.
     */
    public GetFoldersResponse() {
        // Default constructor - no initialization needed beyond field declarations
    }

    /**
     * Gets the list of folders retrieved from SharePoint.
     *
     * @return list of GetFolderResponse objects
     */
    public List<GetFolderResponse> getFolders() {
        return folders;
    }

    /**
     * Builds a GetFoldersResponse from a JSON response.
     *
     * @param jsonResponse the JSON response from SharePoint API
     * @return GetFoldersResponse instance containing parsed folder data
     */
    public static GetFoldersResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List<Map<String, Object>>) jsonMap.get("value");

        final GetFoldersResponse response = new GetFoldersResponse();
        results.stream().forEach(result -> {
            final GetFolderResponse folderResponse = GetFolderResponse.buildFromMap(result);
            response.folders.add(folderResponse);
        });
        return response;
    }
}
