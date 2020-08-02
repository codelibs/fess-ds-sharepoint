package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetFiles extends SharePointApi<GetFilesResponse> {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Files";

    private String serverRelativeUrl = null;

    public GetFiles(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetFiles setServerRelativeUrl(String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFilesResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)));
        SharePointApi.JsonResponse jsonResponse = doRequest(httpGet);
        try {
            return GetFilesResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
