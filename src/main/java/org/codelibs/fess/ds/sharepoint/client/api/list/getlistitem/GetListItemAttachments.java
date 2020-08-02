package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetListItemAttachments extends SharePointApi<GetListItemAttachmentsResponse> {
    private String editLink = null;

    public GetListItemAttachments(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetListItemAttachments setEditLink(String editLink) {
        this.editLink = editLink;
        return this;
    }

    @Override
    public GetListItemAttachmentsResponse execute() {
        if (editLink == null) {
            throw new SharePointClientException("editLink is required.");
        }
        final HttpGet httpGet = new HttpGet(buildUrl());
        JsonResponse jsonResponse = doRequest(httpGet);
        try {
            return GetListItemAttachmentsResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/_api/" + editLink + "/AttachmentFiles";
    }
}
