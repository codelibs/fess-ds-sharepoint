package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetListItemValue extends SharePointApi<GetListItemValueResponse> {
    private String listId = null;
    private String itemId = null;

    public GetListItemValue(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetListItemValue setListId(String listId) {
        this.listId = listId;
        return this;
    }

    public GetListItemValue setItemId(String itemId) {
        this.itemId = itemId;
        return this;
    }

    @Override
    public GetListItemValueResponse execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final HttpGet httpGet = new HttpGet(buildUrl());
        JsonResponse jsonResponse = doRequest(httpGet);
        try {
            return GetListItemValueResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/FieldValuesAsText";
    }
}
