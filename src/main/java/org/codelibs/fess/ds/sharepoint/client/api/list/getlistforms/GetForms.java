package org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetForms extends SharePointApi<GetFormsResponse> {
    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Forms";
    private static final String GETBYTITLE_API_PATH = "_api/lists/getbytitle('{{list_name}}')/Forms";

    private String listId = null;
    private String listName = null;

    public GetForms(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetForms setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    public GetForms setListName(final String listName) {
        this.listName = listName;
        return this;
    }

    @Override
    public GetFormsResponse execute() {
        if (listId == null && listName == null) {
            throw new SharePointClientException("ListID/ListName is required.");
        }
        final HttpGet httpGet;
        if (listId != null) {
            httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{id}}", listId));
        } else {
            httpGet = new HttpGet(siteUrl + "/" + GETBYTITLE_API_PATH.replace("{{list_name}}", listName));
        }
        JsonResponse jsonResponse = doRequest(httpGet);
        return GetFormsResponse.build(jsonResponse);
    }
}
