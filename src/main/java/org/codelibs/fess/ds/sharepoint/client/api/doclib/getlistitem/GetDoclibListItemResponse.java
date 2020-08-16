package org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

public class GetDoclibListItemResponse implements SharePointApiResponse {
    private final String listId;
    private final String itemId;

    public GetDoclibListItemResponse(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    public String getListId() {
        return listId;
    }

    public String getItemId() {
        return itemId;
    }
}
