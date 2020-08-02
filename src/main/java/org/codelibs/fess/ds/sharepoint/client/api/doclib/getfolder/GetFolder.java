package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetFolder extends SharePointApi<GetFolderResponse> {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')";

    private String serverRelativeUrl = null;

    public GetFolder(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetFolder setServerRelativeUrl(String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFolderResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)));
        JsonResponse jsonResponse = doRequest(httpGet);
        try {
            return GetFolderResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
