package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetFoldersResponse implements SharePointApiResponse {
    private final List<GetFolderResponse> folders = new ArrayList<>();

    public List<GetFolderResponse> getFolders() {
        return folders;
    }

    public static GetFoldersResponse build(SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List)jsonMap.get("value");

        final GetFoldersResponse response = new GetFoldersResponse();
        results.stream().forEach(result -> {
            GetFolderResponse folderResponse = GetFolderResponse.buildFromMap(result);
            response.folders.add(folderResponse);
        });
        return response;
    }
}
