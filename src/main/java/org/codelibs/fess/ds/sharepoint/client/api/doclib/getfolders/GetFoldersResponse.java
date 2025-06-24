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

public class GetFoldersResponse implements SharePointApiResponse {
    private final List<GetFolderResponse> folders = new ArrayList<>();

    public List<GetFolderResponse> getFolders() {
        return folders;
    }

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
