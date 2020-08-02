package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetFile  extends SharePointApi<GetFileResponse> {
    private String serverRelativeUrl = null;

    public GetFile(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetFile setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFileResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        httpGet.addHeader("Accept", "application/json");
        try {
            CloseableHttpResponse httpResponse = client.execute(httpGet);
            return new GetFileResponse(httpResponse);
        } catch(Exception e) {
            throw new SharePointClientException("Request failure.", e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/_api/web/GetFileByServerRelativeUrl('" + encodeRelativeUrl(serverRelativeUrl) + "')/$value";
    }
}
