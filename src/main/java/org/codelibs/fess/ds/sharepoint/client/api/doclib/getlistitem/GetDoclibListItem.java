package org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

import java.util.Map;

public class GetDoclibListItem extends SharePointApi<GetDoclibListItemResponse> {
    private String serverRelativeUrl = null;

    public GetDoclibListItem(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetDoclibListItem setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetDoclibListItemResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        final JsonResponse jsonResponse = doRequest(httpGet);
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        try {
            final String itemId = bodyMap.get("Id").toString();
            final String listId = getListId(bodyMap.get("odata.editLink").toString());
            return new GetDoclibListItemResponse(listId, itemId);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodeRelativeUrl(serverRelativeUrl) + "')/ListItemAllFields";
    }

    private String getListId(final String editLink) {
        return editLink.substring(editLink.indexOf("(guid'") + "(guid'".length(), editLink.indexOf("')"));
    }
}
